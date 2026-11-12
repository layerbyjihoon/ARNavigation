package com.google.ar.core.examples.java.helloar.localization;

/** Right-handed +Y rotation and horizontal translation in origin-local meters. */
public final class Rigid2D {
  public final double yaw,x,z;
  public Rigid2D(double yaw,double x,double z){this.yaw=yaw;this.x=x;this.z=z;}
  public double[] apply(double px,double pz){return new double[]{Math.cos(yaw)*px+Math.sin(yaw)*pz+x,-Math.sin(yaw)*px+Math.cos(yaw)*pz+z};}
  public double[] inverse(double px,double pz){double xx=px-x,zz=pz-z;return new double[]{Math.cos(yaw)*xx-Math.sin(yaw)*zz,Math.sin(yaw)*xx+Math.cos(yaw)*zz};}
  public static double wrap(double a){return Math.atan2(Math.sin(a),Math.cos(a));}
  public static Rigid2D identity(){return new Rigid2D(0,0,0);}
}
