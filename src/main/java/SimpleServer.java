import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleServer {

    static final Map<String, List<WsContext>> rooms = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        int port = Integer.parseInt(
                Optional.ofNullable(System.getenv("PORT"))
                        .orElse("8080")
        );

        var app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);

        app.get("/", ctx -> {
            ctx.html(INDEX);
        });

        app.ws("/signal", ws -> {

            ws.onConnect(ctx -> {

                String room = ctx.queryParam("room");

                if (room == null || room.isBlank())
                    room = "default";

                List<WsContext> clients =
                        rooms.computeIfAbsent(
                                room,
                                r -> Collections.synchronizedList(new ArrayList<>())
                        );

                clients.add(ctx);

                System.out.println("JOIN " + room + " (" + clients.size() + ")");

                if (clients.size() == 2) {

                    clients.get(0).send("""
                    {
                      "type":"join"
                    }
                    """);

                }

            });

            ws.onMessage(ctx -> {

                String room = ctx.queryParam("room");

                if (room == null)
                    return;

                List<WsContext> clients = rooms.get(room);

                if (clients == null)
                    return;

                synchronized (clients) {

                    for (WsContext other : clients) {

                        if (other == ctx)
                            continue;

                        if (!other.session.isOpen())
                            continue;

                        other.send(ctx.message());

                    }

                }

            });

            ws.onClose(ctx -> {

                String room = ctx.queryParam("room");

                if (room == null)
                    return;

                List<WsContext> clients = rooms.get(room);

                if (clients == null)
                    return;

                clients.remove(ctx);

                if (clients.isEmpty())
                    rooms.remove(room);

                System.out.println("LEFT " + room);

            });

        });

        System.out.println("Started on " + port);

    }

    static final String INDEX = """
<!DOCTYPE html>
<html lang="ru">

<head>

<meta charset="UTF-8">

<title>Video Chat</title>

<style>

*{
    box-sizing:border-box;
}

body{

    margin:0;

    background:#181818;

    color:white;

    font-family:Arial;

    display:flex;

    flex-direction:column;

    align-items:center;

}

h1{

    margin-top:20px;

}

#videos{

    display:flex;

    gap:20px;

    margin-top:20px;

}

video{

    width:420px;

    height:315px;

    background:black;

    border-radius:12px;

    border:2px solid #555;

    object-fit:cover;

}

button{

    margin-top:20px;

    padding:12px 30px;

    font-size:18px;

    cursor:pointer;

}

#status{

    margin-top:20px;

    color:#66ff66;

}

</style>

</head>

<body>

<h1>Simple Video Chat</h1>

<div id="status">
Waiting...
</div>

<div id="videos">

<video
id="local"
autoplay
muted
playsinline></video>

<video
id="remote"
autoplay
playsinline></video>

</div>

<button id="join">
Join room
</button>

            <script>
            
                     const room = new URL(location.href).searchParams.get("room") ?? "default";
            
                     const status = document.getElementById("status");
            
                     const joinButton = document.getElementById("join");
            
                     const localVideo = document.getElementById("local");
            
                     const remoteVideo = document.getElementById("remote");
            
                     let ws;
                     let pc;
            
                     let localStream;
                     let remoteStream;
            
                     const rtcConfig = {
                                     iceServers:[
                                         {
                                             urls:"stun:stun.l.google.com:19302"
                                         }
                                     ]
                                 };
            
                     joinButton.onclick = join;
            
                     async function join(){
            
                         joinButton.disabled = true;
            
                         status.innerText = "Requesting camera...";
            
                         localStream =
                             await navigator.mediaDevices.getUserMedia({
            
                                 video:true,
                                 audio:false
            
                             });
            
                         localVideo.srcObject = localStream;
            
                         connectSocket();
            
                     }
            
                     function connectSocket(){
            
                         const proto =
                             location.protocol==="https:"
                                 ? "wss"
                                 : "ws";
            
                         ws = new WebSocket(
            
                             proto+
            
                             "://"+
            
                             location.host+
            
                             "/signal?room="+
            
                             encodeURIComponent(room)
            
                         );
            
                         ws.onopen = async ()=>{
            
                             status.innerText="Connected";
            
                             await createPeer();
            
                         };
            
                         ws.onmessage = async e=>{
            
                             const msg = JSON.parse(e.data);
            
                             console.log(msg);
            
                             switch(msg.type){
            
                                 case "join":
            
                                     await makeOffer();
                                     break;
            
                                 case "offer":
            
                                     await receiveOffer(msg);
            
                                     break;
            
                                 case "answer":
            
                                     await receiveAnswer(msg);
            
                                     break;
            
                                 case "candidate":
            
                                     await receiveCandidate(msg);
            
                                     break;
            
                             }
            
                         };
            
                         ws.onclose=()=>{
            
                             status.innerText="Disconnected";
            
                         };
            
                     }
            
                     async function createPeer(){
            
                         pc = new RTCPeerConnection(rtcConfig);
            
                         remoteStream = new MediaStream();
            
                         remoteVideo.srcObject = remoteStream;
            
                         localStream.getTracks().forEach(track=>{
            
                             pc.addTrack(track,localStream);
            
                         });
            
                         pc.ontrack = e=>{
            
                             e.streams[0].getTracks().forEach(track=>{
            
                                 remoteStream.addTrack(track);
            
                             });
            
                         };
            
                         pc.onicecandidate = e=>{
            
                                         if(e.candidate){
            
                                             ws.send(JSON.stringify({
            
                                                 type:"candidate",
            
                                                 candidate:e.candidate
            
                                             }));
            
                                         }
            
                                     };
                                     }
            
                    async function makeOffer(){
            
                                    const offer = await pc.createOffer();
            
            
                                    await pc.setLocalDescription(offer);
            
            
                                    ws.send(JSON.stringify({
            
                                        type:"offer",
            
                                        sdp: {
            
                                            type: offer.type,
            
                                            sdp: offer.sdp
            
                                        }
            
                                    }));
            
                                }
            
                     async function receiveOffer(msg){
            
                                     if(!pc){
            
                                         await createPeer();
            
                                     }
            
            
                                     await pc.setRemoteDescription(msg.sdp);
            
            
                                     const answer = await pc.createAnswer();
            
            
                                     await pc.setLocalDescription(answer);
            
            
                                     ws.send(JSON.stringify({
            
                                         type:"answer",
            
                                         sdp: {
            
                                             type: answer.type,
            
                                             sdp: answer.sdp
            
                                         }
            
                                     }));
            
                                 }
            
                     async function receiveAnswer(msg){
            
                         await pc.setRemoteDescription(msg.sdp);
            
                     }
            
                     async function receiveCandidate(msg){
            
                         if(msg.candidate){
            
                             try{
            
                                 await pc.addIceCandidate(msg.candidate);
            
                             }
            
                             catch(err){
            
                                 console.log(err);
            
                             }
            
                         }
            
                     }
            
                     </script>

</body>

</html>
""";

}