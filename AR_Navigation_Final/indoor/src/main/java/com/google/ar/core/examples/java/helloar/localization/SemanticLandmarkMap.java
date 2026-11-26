package com.google.ar.core.examples.java.helloar.localization;

import com.google.ar.core.examples.java.helloar.coordinates.MapCoordinateTransformer;
import com.google.ar.core.examples.java.helloar.map.*;
import java.util.*;

/** Actual plan coordinates retain survey provenance. No synthetic runtime building landmarks. */
public final class SemanticLandmarkMap {
  public static final class Landmark {
    public final String id,type;public final double x,z;public final boolean verified,planReference;
    public Landmark(String id,String type,double x,double z,boolean verified){this(id,type,x,z,verified,false);}
    public Landmark(String id,String type,double x,double z,boolean verified,boolean planReference){this.id=id;this.type=type;this.x=x;this.z=z;this.verified=verified;this.planReference=planReference;}
    public boolean usable(){return verified||planReference;}
  }
  public static final class Corridor {
    public final double ax,az,bx,bz;
    public Corridor(double ax,double az,double bx,double bz){this.ax=ax;this.az=az;this.bx=bx;this.bz=bz;}
    public double yaw(){return Math.atan2(-(bx-ax),-(bz-az));}
    double distance(double x,double z){double dx=bx-ax,dz=bz-az,length=dx*dx+dz*dz;
      double t=length==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/length));return Math.hypot(x-ax-t*dx,z-az-t*dz);}
  }
  public final String floorId;
  public final List<Landmark> landmarks;
  public final List<Corridor> corridors;
  public static final class RoutePoint {public final double x,z;public RoutePoint(double x,double z){this.x=x;this.z=z;}}
  public final List<RoutePoint> route;
  public SemanticLandmarkMap(String floor,List<Landmark> landmarks){this(floor,landmarks,Collections.emptyList());}
  public SemanticLandmarkMap(String floor,List<Landmark> landmarks,List<Corridor> corridors){this(floor,landmarks,corridors,Collections.emptyList());}
  public SemanticLandmarkMap(String floor,List<Landmark> landmarks,List<Corridor> corridors,List<RoutePoint> route){floorId=floor;this.landmarks=Collections.unmodifiableList(new ArrayList<>(landmarks));this.corridors=Collections.unmodifiableList(new ArrayList<>(corridors));this.route=Collections.unmodifiableList(new ArrayList<>(route));}
  public static SemanticLandmarkMap fromIndoorMap(IndoorMap map,String floor,MapCoordinateTransformer transform) {
    List<Landmark> result=new ArrayList<>();
    for(MapNode node:map.nodes) {
      if(!node.floorId.equals(floor))continue;
      String type=node.type==NodeType.DOOR?"DOOR":node.type==NodeType.STAIR?"STAIRS":null;
      if(type==null)continue;
      MapPosition point=transform.mapToAr(node.position);
      Object reference=node.properties.get("semanticReference");
      boolean verified=Boolean.TRUE.equals(node.properties.get("field_verified"))
          &&Boolean.TRUE.equals(node.properties.get("localizationVerified"))
          &&("DOOR_SURFACE_CENTER_GROUND_PROJECTION".equals(reference)||"STAIR_LANDING_REFERENCE".equals(reference));
      // Existing drawing coordinates are usable approximate references; survey provenance stays false.
      result.add(new Landmark(node.id,type,point.x,point.z,verified,true));
    }
    Map<String,MapNode> nodes=new HashMap<>();for(MapNode node:map.nodes)nodes.put(node.id,node);
    List<Corridor> axes=new ArrayList<>();
    for(MapEdge edge:map.edges){MapNode a=nodes.get(edge.from),b=nodes.get(edge.to);
      if(a==null||b==null||!a.floorId.equals(floor)||!b.floorId.equals(floor)||a.type!=NodeType.WAYPOINT||b.type!=NodeType.WAYPOINT)continue;
      MapPosition p=transform.mapToAr(a.position),q=transform.mapToAr(b.position);
      if(Math.hypot(p.x-q.x,p.z-q.z)>=1)axes.add(new Corridor(p.x,p.z,q.x,q.z));
    }
    return new SemanticLandmarkMap(floor,result,axes);
  }
  public int verifiedCount(){int count=0;for(Landmark p:landmarks)if(p.verified)count++;return count;}
  public int usableCount(){int count=0;for(Landmark p:landmarks)if(p.usable())count++;return count;}
  /** Axis is deliberately unavailable near similarly close perpendicular corridor branches. */
  public Double corridorYaw(double x,double z){
    if(corridors==null)return null;Corridor best=null;double nearest=3;
    for(Corridor c:corridors){double d=c.distance(x,z);if(d<nearest){nearest=d;best=c;}}
    if(best==null)return null;
    for(Corridor c:corridors)if(c.distance(x,z)<=nearest+1.5&&Math.abs(CorridorDirectionEstimator.axisDifference(c.yaw(),best.yaw()))>Math.toRadians(20))return null;
    return best.yaw();
  }
  public SemanticLandmarkMap nearRoute(List<MapPosition> route,double radius) {
    List<Landmark> nearby=new ArrayList<>();
    for(Landmark p:landmarks)for(int i=1;i<route.size();i++) {
      MapPosition a=route.get(i-1),b=route.get(i);double dx=b.x-a.x,dz=b.z-a.z;
      double length=dx*dx+dz*dz,t=length==0?0:Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.z-a.z)*dz)/length));
      if(Math.hypot(p.x-a.x-t*dx,p.z-a.z-t*dz)<=radius){nearby.add(p);break;}
    }
    List<RoutePoint> path=new ArrayList<>();for(MapPosition p:route)path.add(new RoutePoint(p.x,p.z));
    return new SemanticLandmarkMap(floorId,nearby,corridors==null?Collections.emptyList():corridors,path);
  }
}
