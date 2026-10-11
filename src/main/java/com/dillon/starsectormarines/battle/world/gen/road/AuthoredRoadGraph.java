package com.dillon.starsectormarines.battle.world.gen.road;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Road graph from authored routes that meet at their endpoints. */
public final class AuthoredRoadGraph {
    private AuthoredRoadGraph() {}
    /** Route endpoints are junctions; the edge cells follow the same sampled spline as the pavement. */
    public static RoadGraph graph(RoadPlan plan, String excludedRoute) {
        Map<Long,RoadGraph.Node> nodes = new LinkedHashMap<>();
        List<RoadGraph.Edge> edges = new ArrayList<>();
        for(var route:plan.routes()) {
            if(route.id().equals(excludedRoute)) continue;
            List<int[]> cells = new ArrayList<>();
            for(var p:RoadRasterizer.sample(route)) {
                int x=(int)Math.round(p.x()),y=(int)Math.round(p.y());
                if(x<0 || y<0 || x>=plan.width() || y>=plan.height()) continue;
                if(cells.isEmpty() || cells.get(cells.size()-1)[0]!=x || cells.get(cells.size()-1)[1]!=y)
                    cells.add(new int[]{x,y});
            }
            if(cells.size()<2) throw new IllegalStateException("Road has no routable length: "+route.id());
            var a=roadNode(nodes,cells.get(0),plan);
            var b=roadNode(nodes,cells.get(cells.size()-1),plan);
            int[] xs=new int[cells.size()],ys=new int[cells.size()];
            for(int i=0;i<cells.size();i++) {xs[i]=cells.get(i)[0];ys[i]=cells.get(i)[1];}
            edges.add(new RoadGraph.Edge(edges.size(),a,b,xs,ys));
        }
        return new RoadGraph(new ArrayList<>(nodes.values()),edges);
    }
    private static RoadGraph.Node roadNode(Map<Long,RoadGraph.Node> nodes,int[] cell,RoadPlan plan) {
        long key=((long)cell[0]<<32)|(cell[1]&0xffffffffL);
        return nodes.computeIfAbsent(key,k->new RoadGraph.Node(nodes.size(),cell[0],cell[1],
                cell[0]==0 || cell[0]==plan.width()-1 || cell[1]==0 || cell[1]==plan.height()-1));
    }
}
