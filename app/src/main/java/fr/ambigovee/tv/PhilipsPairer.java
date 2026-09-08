package fr.ambigovee.tv;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

final class PhilipsPairer {
    private static final String SECRET="JCqdN5AcnAHgJYseUn7ER5k3qgtemfUvMRghQpTfTZq7Cvv8EPQPqfz6dDxPQPSu4gKFPWkJGw32zyASgJkHwCjU";
    private static final Pattern PAIR=Pattern.compile("([a-zA-Z0-9_]+)=(?:\\\"([^\\\"]*)\\\"|([^,\\s]+))");
    static final class Pending { final String base,deviceId,authKey; final long timestamp; Pending(String base,String deviceId,String authKey,long timestamp){this.base=base;this.deviceId=deviceId;this.authKey=authKey;this.timestamp=timestamp;} }
    static final class Result { final String ip,user,key; Result(String ip,String user,String key){this.ip=ip;this.user=user;this.key=key;} }

    private final SSLSocketFactory ssl; private final HostnameVerifier verifier=(h,s)->true; private final SecureRandom random=new SecureRandom();
    PhilipsPairer() throws Exception {TrustManager[] all={new X509TrustManager(){public void checkClientTrusted(X509Certificate[]c,String a){}public void checkServerTrusted(X509Certificate[]c,String a){}public X509Certificate[]getAcceptedIssuers(){return new X509Certificate[0];}}};SSLContext x=SSLContext.getInstance("TLS");x.init(null,all,new SecureRandom());ssl=x.getSocketFactory();}

    Pending begin() throws Exception {
        String local=PhilipsClient.localIpv4();String[] hosts=local!=null?new String[]{local,"127.0.0.1"}:new String[]{"127.0.0.1"};Exception last=null;
        for(String host:hosts){try{return beginAt(host);}catch(Exception e){last=e;}}
        if(last!=null)throw last;throw new IllegalStateException("TV Philips introuvable");
    }

    private Pending beginAt(String host) throws Exception {
        String base="https://"+host+":1926";String deviceId=randomId();
        JSONObject device=device(deviceId);JSONObject body=new JSONObject();body.put("scope",new JSONArray().put("read").put("write").put("control"));body.put("device",device);
        Raw r=post(base,"/6/pair/request",body.toString(),null);
        if(r.code<200||r.code>=300)throw new IllegalStateException("Pair request HTTP "+r.code);
        JSONObject j=new JSONObject(r.body);if(!"SUCCESS".equalsIgnoreCase(j.optString("error_id")))throw new IllegalStateException(j.optString("error_text","Pairing refusé"));
        return new Pending(base,deviceId,j.getString("auth_key"),j.getLong("timestamp"));
    }

    Result grant(Pending p,String pin) throws Exception {
        pin=pin.trim();if(pin.length()<4)throw new IllegalArgumentException("PIN invalide");
        JSONObject auth=new JSONObject();auth.put("auth_AppId","1");auth.put("pin",pin);auth.put("auth_timestamp",p.timestamp);auth.put("auth_signature",signature(p.timestamp,pin));
        JSONObject body=new JSONObject();body.put("auth",auth);body.put("device",device(p.deviceId));
        String path="/6/pair/grant";Raw first=post(p.base,path,body.toString(),null);Raw response=first;
        if(first.code==HttpURLConnection.HTTP_UNAUTHORIZED){Digest d=parse(first.challenge);AtomicInteger nc=new AtomicInteger(1);String header=digestHeader(d,"POST",path,p.deviceId,p.authKey,nc);response=post(p.base,path,body.toString(),header);if(response.code==HttpURLConnection.HTTP_UNAUTHORIZED){d=parse(response.challenge);header=digestHeader(d,"POST",path,p.deviceId,p.authKey,new AtomicInteger(1));response=post(p.base,path,body.toString(),header);}}
        if(response.code<200||response.code>=300)throw new IllegalStateException("Pair grant HTTP "+response.code);
        JSONObject j=new JSONObject(response.body);if(j.has("error_id")&&!"SUCCESS".equalsIgnoreCase(j.optString("error_id")))throw new IllegalStateException(j.optString("error_text","PIN refusé"));
        String ip=p.base.replace("https://","").replace(":1926","");return new Result(ip,p.deviceId,p.authKey);
    }

    private JSONObject device(String id)throws Exception{JSONObject d=new JSONObject();d.put("device_name","AmbiGovee TV");d.put("device_os","Android TV");d.put("app_name","AmbiGovee");d.put("type","native");d.put("app_id","fr.ambigovee.tv");d.put("id",id);return d;}
    private String signature(long ts,String pin)throws Exception{byte[] key=Base64.decode(SECRET,Base64.DEFAULT);Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(key,"HmacSHA1"));byte[] digest=mac.doFinal((String.valueOf(ts)+pin).getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(Locale.US,"%02x",b&255));return Base64.encodeToString(hex.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);}

    private Raw post(String base,String path,String body,String authorization)throws Exception{HttpsURLConnection c=(HttpsURLConnection)new URL(base+path).openConnection();c.setSSLSocketFactory(ssl);c.setHostnameVerifier(verifier);c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(700);c.setReadTimeout(1800);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");if(authorization!=null)c.setRequestProperty("Authorization",authorization);try(OutputStream os=c.getOutputStream()){os.write(body.getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();String ch=c.getHeaderField("WWW-Authenticate");InputStream s=code>=200&&code<400?c.getInputStream():c.getErrorStream();String text=read(s);c.disconnect();return new Raw(code,text,ch);}
    private String digestHeader(Digest d,String method,String uri,String user,String key,AtomicInteger count)throws Exception{String cnonce=randomId(),nc=String.format(Locale.US,"%08x",count.getAndIncrement());String ha1=md5(user+":"+d.realm+":"+key);if("MD5-sess".equalsIgnoreCase(d.algorithm))ha1=md5(ha1+":"+d.nonce+":"+cnonce);String ha2=md5(method+":"+uri);String resp=d.qop!=null?md5(ha1+":"+d.nonce+":"+nc+":"+cnonce+":"+d.qop+":"+ha2):md5(ha1+":"+d.nonce+":"+ha2);StringBuilder h=new StringBuilder("Digest username=\"").append(user).append("\", realm=\"").append(d.realm).append("\", nonce=\"").append(d.nonce).append("\", uri=\"").append(uri).append("\", response=\"").append(resp).append("\"");if(d.algorithm!=null)h.append(", algorithm=").append(d.algorithm);if(d.qop!=null)h.append(", qop=").append(d.qop).append(", nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");if(d.opaque!=null)h.append(", opaque=\"").append(d.opaque).append("\"");return h.toString();}
    private Digest parse(String header){if(header==null)throw new IllegalStateException("Digest absent");Map<String,String>m=new HashMap<>();Matcher x=PAIR.matcher(header);while(x.find())m.put(x.group(1).toLowerCase(Locale.US),x.group(2)!=null?x.group(2):x.group(3));String raw=m.get("qop"),q=null;if(raw!=null){for(String c:raw.split(","))if("auth".equalsIgnoreCase(c.trim())){q="auth";break;}}return new Digest(m.get("realm"),m.get("nonce"),q,m.getOrDefault("algorithm","MD5"),m.get("opaque"));}
    private String randomId(){String chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";StringBuilder s=new StringBuilder();for(int i=0;i<16;i++)s.append(chars.charAt(random.nextInt(chars.length())));return s.toString();}
    private static String md5(String s)throws Exception{byte[] r=MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.ISO_8859_1));StringBuilder o=new StringBuilder();for(byte b:r)o.append(String.format(Locale.US,"%02x",b&255));return o.toString();}
    private static String read(InputStream s)throws Exception{if(s==null)return"";StringBuilder o=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(s,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)o.append(l);}return o.toString();}
    private static final class Raw{final int code;final String body,challenge;Raw(int c,String b,String h){code=c;body=b;challenge=h;}}
    private static final class Digest{final String realm,nonce,qop,algorithm,opaque;Digest(String r,String n,String q,String a,String o){realm=r;nonce=n;qop=q;algorithm=a;opaque=o;}}
}
