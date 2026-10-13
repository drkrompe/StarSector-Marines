package com.dillon.starsectormarines.campaign;

import java.io.Serializable;

/** Attempt receipt for the cooperative's first recovery contract; no fleet hull is awarded here. */
public final class ShepherdRecoveryProgress implements Serializable {
    public static final String PREFIX="basin_salvage_v1_";
    public static final int PAYMENT=18000;
    private int attempt=1, inspectionLosses;
    private String activeId, activePlanet;
    private boolean claimSecured;
    public boolean claimSecured(){return claimSecured;}
    public int inspectionLosses(){return inspectionLosses;}
    public String missionId(){return claimSecured?null:PREFIX+attempt;}
    public boolean matches(String id){return id!=null && id.equals(missionId());}
    public static boolean owns(String id){return id!=null && id.startsWith(PREFIX);}
    public boolean begin(String id,String planet){
        if(!matches(id)||activeId!=null||planet==null||planet.isBlank())return false;
        activeId=id;activePlanet=planet;return true;
    }
    public int settle(String id,String planet,boolean victory,int payment,int crewLost,boolean inspected){
        if(!matches(id)||!id.equals(activeId)||!activePlanet.equals(planet)
                ||payment!=(victory?PAYMENT:0)||crewLost<0||crewLost>2||victory!=inspected
                ||victory && crewLost==2)return -1;
        inspectionLosses+=crewLost;
        activeId=null;activePlanet=null;attempt++;
        claimSecured=victory;
        return payment;
    }
}
