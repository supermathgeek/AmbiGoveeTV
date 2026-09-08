package fr.ambigovee.tv;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class ColorEngine {
    private static final double TOP_WEIGHT=2.15, SIDE_WEIGHT=1.0, BOTTOM_WEIGHT=0.75, SATURATION_BOOST=1.12;
    private static final int AUTO_MAX_BRIGHTNESS=80;

    static final class Target {
        final double r,g,b,brightness;
        Target(double r,double g,double b,double brightness){this.r=r;this.g=g;this.b=b;this.brightness=brightness;}
    }
    private static final class Sample {
        final double r,g,b,w;
        Sample(double r,double g,double b,double w){this.r=r;this.g=g;this.b=b;this.w=w;}
    }

    static Target fromMeasured(Context context, JSONObject payload) throws Exception {
        return fromMeasured(context, payload, GoveeConfig.POSITION_ROOM);
    }

    static Target fromMeasured(Context context, JSONObject payload, String position) throws Exception {
        JSONObject layer=payload.optJSONObject("layer1");
        if(layer==null) return new Target(0,0,0,1);

        String wanted = GoveeConfig.normalizePosition(position);
        List<Sample> samples = collect(layer, wanted);
        // Si une TV n'a pas de LEDs sur le côté choisi (ex. pas de bottom),
        // on retombe automatiquement sur la pièce entière au lieu d'envoyer noir.
        if(samples.isEmpty() && !GoveeConfig.POSITION_ROOM.equals(wanted)) samples = collect(layer, GoveeConfig.POSITION_ROOM);
        if(samples.isEmpty()) return new Target(0,0,0,1);

        double ws=0,rr=0,gg=0,bb=0;
        List<Double> lum=new ArrayList<>();
        for(Sample s:samples){
            ws+=s.w; rr+=s.r*s.r*s.w; gg+=s.g*s.g*s.w; bb+=s.b*s.b*s.w;
            lum.add(0.2126*s.r+0.7152*s.g+0.0722*s.b);
        }
        double r=Math.sqrt(rr/ws),g=Math.sqrt(gg/ws),b=Math.sqrt(bb/ws);
        float[] hsv=new float[3];
        Color.RGBToHSV(clamp((int)Math.round(r),0,255),clamp((int)Math.round(g),0,255),clamp((int)Math.round(b),0,255),hsv);
        hsv[1]=Math.min(1f,(float)(hsv[1]*SATURATION_BOOST));
        int boosted=Color.HSVToColor(hsv);
        r=Color.red(boosted);g=Color.green(boosted);b=Color.blue(boosted);

        Collections.sort(lum);
        double light=percentile(lum,0.75);
        double n=Math.max(0,Math.min(1,light/255.0));
        double brightness=1+Math.pow(n,0.76)*(AUTO_MAX_BRIGHTNESS-1);
        return new Target(r,g,b,brightness);
    }

    private static List<Sample> collect(JSONObject layer, String wanted) {
        List<Sample> samples = new ArrayList<>();
        Iterator<String> sides=layer.keys();
        while(sides.hasNext()){
            String side=sides.next();
            if (!GoveeConfig.POSITION_ROOM.equals(wanted) && !wanted.equals(side)) continue;
            JSONObject zones=layer.optJSONObject(side); if(zones==null)continue;
            double sw;
            if (!GoveeConfig.POSITION_ROOM.equals(wanted)) sw = 1.0;
            else sw="top".equals(side)?TOP_WEIGHT:("bottom".equals(side)?BOTTOM_WEIGHT:SIDE_WEIGHT);
            Iterator<String> keys=zones.keys();
            while(keys.hasNext()){
                JSONObject z=zones.optJSONObject(keys.next()); if(z==null)continue;
                double r=z.optDouble("r",0),g=z.optDouble("g",0),b=z.optDouble("b",0);
                double value=Math.max(r,Math.max(g,b))/255.0;
                samples.add(new Sample(r,g,b,sw*(0.48+0.52*value)));
            }
        }
        return samples;
    }

    static double alpha(double dt,double tau){return 1-Math.exp(-Math.max(0.0001,dt)/Math.max(0.0001,tau));}
    static double rgbDelta(double r1,double g1,double b1,double r2,double g2,double b2){return Math.abs(r1-r2)+Math.abs(g1-g2)+Math.abs(b1-b2);}
    private static double percentile(List<Double> v,double p){if(v.isEmpty())return 0;if(v.size()==1)return v.get(0);double x=(v.size()-1)*p;int lo=(int)Math.floor(x),hi=(int)Math.ceil(x);if(lo==hi)return v.get(lo);double f=x-lo;return v.get(lo)*(1-f)+v.get(hi)*f;}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
