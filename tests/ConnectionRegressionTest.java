package com.tgwgroup.MiRearScreenSwitcher;
import android.content.*;
import android.os.*;
import rikka.shizuku.Shizuku;

public final class ConnectionRegressionTest {
    private static int checks;
    private static void check(boolean condition,String message) { checks++; if(!condition) throw new AssertionError(message); }
    private static final class Token implements IBinder { boolean alive=true; public boolean isBinderAlive(){return alive;} }
    private static final class Client implements ServiceConnection {
        IBinder received; int connects, disconnects;
        public void onServiceConnected(ComponentName name,IBinder binder){received=binder; connects++;}
        public void onServiceDisconnected(ComponentName name){received=null; disconnects++;}
    }
    private static void bind(Client client) { TaskServiceClient.bind(null,client); Handler.drain(); }
    private static void unbind(Client client) { TaskServiceClient.unbind(null,client,false); Handler.drain(); }
    private static ServiceConnection latest() { return Shizuku.attempts.get(Shizuku.attempts.size()-1); }
    public static void main(String[] args) {
        Client a=new Client(),b=new Client();
        bind(a); bind(b);
        check(Shizuku.attempts.size()==1,"two owners must share one bind");
        Token token=new Token(); latest().onServiceConnected(null,token); Handler.drain();
        check(a.received==token && b.received==token,"both owners receive service");
        bind(a); check(a.connects==1,"duplicate bind does not duplicate delivery");
        unbind(a); check(Shizuku.removes==0,"one owner cannot destroy another owner's service");
        unbind(b); check(Shizuku.removes==1,"last owner removes service");

        a=new Client(); bind(a); ServiceConnection stale=latest();
        Handler.advance(6000); ServiceConnection current=latest();
        check(stale!=current,"timeout must create a new attempt");
        stale.onServiceConnected(null,new Token()); Handler.drain();
        check(a.connects==0,"late stale connection must be ignored");
        token=new Token(); current.onServiceConnected(null,token); Handler.drain();
        check(a.received==token,"current attempt remains deliverable");
        token.alive=false; int before=Shizuku.attempts.size(); bind(a);
        check(Shizuku.attempts.size()==before+1 && a.disconnects==1,"explicit request detects dead cached binder");
        unbind(a);

        a=new Client(); before=Shizuku.attempts.size(); bind(a); Handler.advance(120000);
        check(Shizuku.attempts.size()==before+6,"one initial attempt plus exactly five bounded retries");
        Handler.advance(120000);
        check(Shizuku.attempts.size()==before+6,"retry exhaustion must stop background work");
        bind(a); check(Shizuku.attempts.size()==before+7,"new caller request can recover after retry exhaustion");
        latest().onServiceConnected(null,new Token()); Handler.drain();
        check(a.connects==1,"recovery is actually delivered"); unbind(a);

        a=new Client(); Shizuku.permitted=false; before=Shizuku.attempts.size(); bind(a); Handler.advance(120000);
        check(Shizuku.attempts.size()==before,"denied permission must not spin");
        Shizuku.permitted=true; Shizuku.permission.onRequestPermissionResult(0,0); Handler.drain();
        check(Shizuku.attempts.size()==before+1,"permission event starts a connection"); unbind(a);
        System.out.println("PASS: "+checks+" connection lifecycle assertions");
    }
}
