/**
 * @author Haoyang Yuan (hangyangy)
 * The usernode source file to implement a user client.
 * Which will receive the commit request from the Server
 * and then it will check if the request images are available
 * also it will ask the user oppion the vote for the result.
 * The User client will write down log in case of failure, and
 * to recovery from the failure.
 */
import java.io.*;
import java.util.*;

/*** UserNode.java ****/
public class UserNode implements ProjectLib.MessageHandling {

	public static String myId;
	private static ProjectLib PL;
	private static String logName = null;

	/* The locked images and deleted images */
	private static Map<String,Set<String>> piclocked = new HashMap<>();
	private static Set<String> picdeleted = new HashSet<>();

	/**
	 * The constructor
	 * @param id - the id of the user client
     */
	public UserNode( String id ) {
		myId = id;
	}

	/**
	 * Recieve the request and decisions and process it
	 * @param msg - the received message
	 * @return true if the message has been processed, false if not
     */
	public boolean deliverMessage( ProjectLib.Message msg ) {

		/* parse message */
		ImgMessage message = Helper.deserialize(msg.body);

		/* ask for vote to a picture */
		if(message.type==1) {
			processVote(message);
		}

		/* get the result of a vote process */
		else if(message.type==3){
			processResult(message);
		}
		return true;
	}

	/**
	 * Check if the client should vote true or false
	 * depends on the User oppinion and images availability
	 * @param message - the collages message information
	 * @return true if vote yes, false if vote no
     */
	public boolean checkVote (ImgMessage message){

		/* If the imformation is sent to a wrong user client*/
		if(message.user.equals(myId)==false){
			return false;
		}

		/* If the request has been received once */
		if(piclocked.containsKey(message.filename)){
			return  false;
		}

		String[] eles = message.imgele.toArray(
					new String[message.imgele.size()]);

		/* If user feel unsatisfied with it */
		if (PL.askUser(message.img, eles)==false){
			return  false;
		}

		/* check if the source images are deleted or locked */
		for(String pic : message.imgele){
			if(picdeleted.contains(pic) ){
				return false;
			}
			for( String filename : piclocked.keySet()){
				if(piclocked.get(filename).contains(pic)){
					return  false;
				}
			}
		}
		return true;
	}

	/**
	 * Process the received vote request
	 * @param message - the request informtion message
     */
	public void processVote(ImgMessage message){

		/*form a giant log string */
		StringBuffer logstring = new StringBuffer();
		logstring.append(message.filename);

		for(String ele : message.imgele){
			logstring.append(";"+ele);
		}

		/* If the user client vote no the for request */
		if (checkVote(message)==false){
			Helper.writeLog(PL,logName,"Vote;false;"+logstring.toString());

			/* send vote response */
			formMessage(2,message.filename,false);
		}
		/* If the user client vote yes for the request */
		else{
			Helper.writeLog(PL,logName,"Vote;true;"+logstring.toString());

			/* lock the source images */
			piclocked.put(message.filename,new HashSet<String>());
			Set pics = piclocked.get(message.filename);

			for(String ele : message.imgele){
				pics.add(ele);
			}

			/* send vote response */
			formMessage(2,message.filename,true);
		}
	}

	/**
	 * Process the vote decision
	 * @param message - the message contains the vote decision
     */
	public void processResult(ImgMessage message){

		/* used to vote for no, do nothing */
		if(piclocked.containsKey(message.filename)==false) {
			/* Still send ACK */
			formMessage(4,message.filename,message.vote);
			return;
		}

		Helper.writeLog(PL,logName,
				"Decision;"+message.vote+";"+message.filename);

		/* If the vote is true */
		if(message.vote==true){

			/* delete the locked files */
			for(String ele : piclocked.get(message.filename)) {
				File file = new File(ele);
				picdeleted.add(ele);
				file.delete();
			}
		}
		/* If the vote is false, do nothing*/
		else{

		}

		/* return Ack */
		piclocked.remove(message.filename);
		formMessage(4,message.filename,message.vote);
	}

	/**
	 * Form and send a message according to the parameters
	 * @param type - the type of response, to vote or ACK
	 * @param filename - the name of the collage
	 * @param result - the voting result
     */
	public void formMessage(int type, String filename, boolean result){
		ImgMessage message = new ImgMessage(type,filename,myId,result);
		ProjectLib.Message msg =
				new ProjectLib.Message("Server",Helper.serialize(message));
		PL.sendMessage( msg );
	}

	/**
	 * Parse the logs to recovery to broken status
	 * @param logs - the log files
     */
	public static void parseLog(List<String> logs){

		/* There is no log information when first start */
		if( logs==null ){
			return;
		}

		for(String str : logs){
			String[] content = str.split(";");

			/* If once voted yes, the files need to be locked */
			if (content[0].equals("Vote") && content[1].equals("true")){
				Set<String> lockSet = new HashSet<String>();
				piclocked.put(content[2],lockSet);
				for(int i=3;i<content.length;i++){
					lockSet.add(content[i]);
				}
			}
			/* If once decison has been made */
			else if(content[0].equals("Decision")){
				/* if threr are any pics locked, need unlock */
				if(content[1].equals("false")
						&& piclocked.containsKey(content[2])){
					piclocked.remove(content[2]);
				}
				/* if threr are any pics locked, need delete */
				else if(content[1].equals("true")){
					for(String ele : piclocked.get(content[2])) {
						File file = new File(ele);
						if(file.exists()){
							file.delete();
						}
						picdeleted.add(ele);
					}
					piclocked.remove(content[2]);
				}
			}
		}
	}

	/**
	 * The main function start working when the server starts
	 * @param args
	 * @throws Exception
     */
	public static void main ( String args[] ) throws Exception {
		if (args.length != 2) throw new Exception("Need 2 args: <port> <id>");
		UserNode UN = new UserNode(args[1]);

		/* Define the name of the log file */
		logName = myId+".log";

		List<String> logs = Helper.readLog(logName);
		parseLog(logs);
		PL = new ProjectLib( Integer.parseInt(args[0]), args[1], UN );

		/* infinite loop to avoid shutdown */
		while(true){

		}
	}

}

