package jp.bunkaich.sukashimotion;

/** Keeps inner content width stable, with gentle vertical compensation and cover parallax. */
final class GlassProjection {
    // A conservative fixed perspective. The earlier 4-panel reference removed 22%
    // of the far edge's height at 120 degrees and looked pinched on the real Fold7.
    // This keeps 94.6% at 120 degrees, close to the accepted gentle taper.
    static final float REFERENCE_DISTANCE = 16; // In half-panel widths; not a measured eye position.
    record Pose(float expansion,float taper) {}
    record Plane(float depth) {}
    record Point(float x,float y) {}
    static Pose coverPose(float angle){
        float t=Math.min(1,clamp(angle)/90);t=t*t*(3-2*t);
        // Slightly stronger expansion gives more pronounced perspective compression
        // at the far edge, closer to a real frosted glass pane tilting away.
        return new Pose(.42f*t,.155f*t);
    }
    static Plane innerPlane(float angle){
        // Hold the vertical compensation below 90 degrees, where this face is hidden.
        // Do not compensate horizontal foreshortening: cosine sampling collapsed
        // the source to a narrow strip and visibly stretched icons near 90 degrees.
        double rotation=Math.toRadians(Math.min(90,180-clamp(angle)));
        return new Plane((float)Math.sin(rotation)/REFERENCE_DISTANCE);
    }
    static float clamp(float angle){return Math.max(0,Math.min(180,angle));}
    static float rearWeight(float angle){float t=Math.max(0,Math.min(1,(angle-20)/60));return t*t*(3-2*t);}
    static Point sample(float x,float y,float width,float height,float angle,boolean inner){
        float page=inner?width*.5f:width,hinge=inner?page:0;
        if(inner&&x>=hinge)return new Point(x,y);
        float distance=(inner?(hinge-x):x)/page;
        if(inner){
            Plane p=innerPlane(angle);float denominator=1-distance*p.depth;
            return new Point(x,
                    height*.5f+(y-height*.5f)/denominator);
        }
        Pose p=coverPose(angle);
        float u=distance/(1+p.expansion),heightScale=1-p.taper*u;
        return new Point(hinge+(inner?-1:1)*page*u,height*.5f+(y-height*.5f)/heightScale);
    }
}
