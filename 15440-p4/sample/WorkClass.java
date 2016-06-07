/**
 * @author Haoyang Yuan (hangyangy)
 * The Work class to process all the procedures of a collage.
 * Send requests and Processing voting results, distribute
 * the results and receive the ACK responses.
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/*** WorkClass.java ***/
public class WorkClass{
    /* The collage information */
    public String filename = null;
    public Set<String> users  = new HashSet<>();
    public Set<String> usersreplied  = new HashSet<>();
    public Set<String> useracked = new HashSet<>();
    public byte[] img = null;

    /* The decision status */
    public boolean decided = false;
    public boolean decision = false;
    public String status = null;

    /* The Work status */
    private final static  String commitStatus = "Commit";
    private final static  String decisionStatus = "Decision";
    private final static  String finishStatus = "Finish";
    private static String logName = "Server.log";

    /* The Original voting request message */
    private String[] sources = null;
    private static ProjectLib PL = null;
    private Map<String,ImgMessage> messages = new HashMap<>();

    /* The timer to invalidate vote and resend ACK */
    private Timer voteTimer = new Timer();
    private Timer ackTimer = new Timer();

    /**
     * Constructor
     * @param _PL - the ProjrctLib of the Server
     * @param _filename - the name of the collage image
     * @param _img - the content of the collage image
     * @param _sources - the sources of the image
     */
    public WorkClass(ProjectLib _PL, String _filename,
                     byte[] _img, String[] _sources) {
        this.filename = _filename;
        this.PL = _PL;
        this.img = _img;
        this.sources = _sources;

		/* parse the source */
        users = new HashSet<>();
        for( String source : sources) {
            String user = source.split(":")[0];
            users.add(user);
            String pic = source.split(":")[1];

            /* Construct the voting request message */
            if(messages.containsKey(user)==false){
                messages.put(user,new ImgMessage(1,filename,user,pic,img));
            }else {
                messages.get(user).addEle(pic);
            }
        }
    }

    /**
     * Process the received message from user client
     * @param message - the message content
     * @return
     */
    public boolean processMsg(ImgMessage message){

        String user = message.user;
        boolean vote = message.vote;

        /* Ack message */
        if(message.type==4){
            if(checkAck(user)==true) {
                /* All the ack have been gained */
                return true;
            }
            return false;
        }

        /* timeout or received decisions, decision has been made */
        if(decided) {
            return false;
        }

        if(vote==true){
            /* fine decision is true */
            if(checkResult(user,vote)==true){

                commitWrite(filename, img);
                decision = message.vote;
                voteTimer.cancel();
                Helper.writeLog(PL,logName,decisionStatus+";"+filename+";true");

                /* tell the result */
                distributeVote(decision);
                startAckTimer();
            }
        }
        /* if any of the answer is no */
        else {
            Helper.writeLog(PL,logName,decisionStatus+";"+filename+";false");
            decision = false;
            distributeVote(decision);
            startAckTimer();
        }
        return false;
    }

    /**
     * To start count the ACK, keep on distribution until all received
     */
    public void startAckTimer(){
        ackTimer.schedule( new ackTimerTask(), 3000, 3000);
    }

    /**
     * Start the voting process, send out the vote requests
     */
    public void startVote(){

        /* form the message */
        for( String user : users){

            byte[] content = Helper.serialize(messages.get(user));
            ProjectLib.Message msg = new ProjectLib.Message(user,content);

			/* send out the message one by one */
            PL.sendMessage( msg );
            messages.remove(user);
        }

        /* Timer started to count the time */
        voteTimer.schedule( new voteTimerTask(),3000,3000);
    }

    /**
     * To Write down the image to the file disk
     * @param filename - the name of the collage
     * @param img - the content of the collage
     */
    private static void commitWrite(String filename, byte[] img) {
        try {
            Files.write(new File(filename).toPath(), img);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Distribute the voting results to all the user clients
     * @param vote - the vote results
     */
    public void distributeVote(boolean vote){
        for(String user : users){

            /* filter the user returned ack */
            if(useracked.contains(user)){
                continue;
            }

            /* keep on notify the user about decision  */
            ImgMessage message = new ImgMessage(3,filename,user,vote);
            byte[] content = Helper.serialize(message);
            ProjectLib.Message msg = new ProjectLib.Message(user,content);

            PL.sendMessage( msg );
        }

        /* decided, no more response cared */
        decided = true;
    }


    /**
     * To check if the voting results is true or false
     * @param user - the user which sends the voting response
     * @param result - the voting response
     * @return true if all user sends true, false if not
     */
    public boolean checkResult(String user, boolean result){
        if(users.contains(user)==false){
            return false;
        }
        usersreplied.add(user);
        /* When all the users send back true */
        if(users.size()==usersreplied.size()){
            return true;
        }
        return false;
    }

    /**
     * To check if all the Ack has been received from the user
     * @param user - the user which sends the ACK information
     * @return true if all ack has been recieved, false if not
     */
    public boolean checkAck(String user){
        if(users.contains(user)==false){
            return false;
        }
        useracked.add(user);
        if(users.size()==useracked.size()){
            Helper.writeLog(PL,logName,finishStatus+";"+filename);

            ackTimer.cancel();
            return true;
        }
        return false;
    }

    /*** ackTimerTask.java ***/
    private class ackTimerTask extends TimerTask{

        public  ackTimerTask(){

        }

        /**
         * to run the object and distribute decision
         * util all ack has been received
         */
        @Override
        public  void run(){
            distributeVote(decision);
        }
    }

    /*** voteTimerTask ***/
    private class voteTimerTask extends TimerTask{

        public  voteTimerTask(){

        }

        /**
         * to distribute false decision when timeout
         */
        @Override
        public  void run(){
            voteTimer.cancel();
            if(decided==false) {
                Helper.writeLog(PL,logName,
                        decisionStatus+";"+filename+";false");
                decision = false;
                distributeVote(decision);
                startAckTimer();
            }
        }
    }
}