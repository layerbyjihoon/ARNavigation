package com.google.ar.core.examples.java.helloar.segmentation;

import ai.onnxruntime.*;
import android.content.res.AssetManager;
import android.util.Log;
import com.google.gson.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One CPU ONNX session, used only on its owning inference executor. */
public final class SegFormerRunner implements AutoCloseable {
  private final OrtEnvironment environment;
  private final OrtSession session;
  private final String inputName,outputName;
  public final int width,height;
  public final float[] mean=new float[3],std=new float[3];
  public final String[] names;
  private final FloatBuffer input;
  public SegFormerRunner(AssetManager assets) throws IOException,OrtException {
    JsonObject pre=json(assets,"segformer_preprocessor_config.json");
    JsonObject size=pre.getAsJsonObject("size");width=size.get("width").getAsInt();height=size.get("height").getAsInt();
    for(int i=0;i<3;i++){mean[i]=pre.getAsJsonArray("image_mean").get(i).getAsFloat();std[i]=pre.getAsJsonArray("image_std").get(i).getAsFloat();}
    JsonObject labels=json(assets,"segformer_config.json").getAsJsonObject("id2label");
    names=new String[labels.size()];for(Map.Entry<String,JsonElement> entry:labels.entrySet())names[Integer.parseInt(entry.getKey())]=entry.getValue().getAsString();
    environment=OrtEnvironment.getEnvironment();
    try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()) {
      options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);
      session=environment.createSession(read(assets,"segformer.onnx"),options);
    }
    inputName=session.getInputNames().iterator().next();outputName=session.getOutputNames().iterator().next();
    TensorInfo info=(TensorInfo)session.getInputInfo().get(inputName).getInfo();
    if(info.type!=OnnxJavaType.FLOAT || info.getShape().length!=4){session.close();throw new IOException("Expected float NCHW input");}
    Log.i("SegFormer","Inputs="+session.getInputInfo()+" outputs="+session.getOutputInfo()+" size="+width+"x"+height);
    input=ByteBuffer.allocateDirect(3*width*height*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
  }
  public SegmentationResult infer(CameraFramePreprocessor.Yuv image,FrameSnapshot frame) throws OrtException {
    CameraFramePreprocessor.writeNchw(image,frame.rotationDegrees,width,height,mean,std,input);
    return inferNchw(input,frame);
  }
  public SegmentationResult inferNchw(FloatBuffer values,FrameSnapshot frame) throws OrtException {
    long start=System.nanoTime();
    try(OnnxTensor tensor=OnnxTensor.createTensor(environment,values,new long[]{1,3,height,width});
        OrtSession.Result result=session.run(Collections.singletonMap(inputName,tensor))) {
      OnnxTensor logits=(OnnxTensor)result.get(outputName).orElseThrow(()->new IllegalStateException("No logits"));
      long[] shape=logits.getInfo().getShape();
      if(shape.length!=4 || shape[0]!=1 || shape[1]!=names.length)throw new IllegalStateException("Unexpected logits "+Arrays.toString(shape));
      int h=(int)shape[2],w=(int)shape[3],pixels=w*h;
      FloatBuffer scores=logits.getFloatBuffer();
      byte[] mask=new byte[pixels];float[] confidence=new float[pixels];
      for(int p=0;p<pixels;p++) {
        int best=0;float max=Float.NEGATIVE_INFINITY;
        for(int k=0;k<names.length;k++){float score=scores.get(k*pixels+p);if(score>max){max=score;best=k;}}
        double sum=0;for(int k=0;k<names.length;k++)sum+=Math.exp(scores.get(k*pixels+p)-max);
        mask[p]=(byte)best;confidence[p]=(float)(1/sum);
      }
      return new SegmentationResult(frame,w,h,mask,confidence,names,(System.nanoTime()-start)/1e6);
    }
  }
  private static JsonObject json(AssetManager assets,String name)throws IOException {
    return JsonParser.parseString(new String(read(assets,name),StandardCharsets.UTF_8)).getAsJsonObject();
  }
  private static byte[] read(AssetManager assets,String name)throws IOException {
    try(InputStream in=assets.open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
      byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();
    }
  }
  @Override public void close()throws OrtException {session.close();}
}
