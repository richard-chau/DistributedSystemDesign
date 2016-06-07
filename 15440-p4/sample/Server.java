/**
 * @author Haoyang Yuan (hangyangy)
 * The Server source file for 15640 course project 4
 * This impelements a failure-resistant Server process
 * That will ask user about the satisfication of image
 * in two phase commitment and then commit the images when
 * all users agree. At the same time, the server uses log
 * to write status information which will help recovery when
 * the server breaks down randomly.
 */

import java.util.*;

/*** Server.java ***/
public class Server implements ProjectLib.CommitServing {

	/* the constant server status for each commit work */
	private final static  String commitStatus = "Commit";
	private final static  String decisionStatus = "Decision";
	private final static  String finishStatus = "Finish";

	private static String logName = "Server.log";
	private static ProjectLib PL;

	/* The map to store all the commit Work inforrmation */
	private static Map<String,WorkClass> tasks =
						new HashMap<String,WorkClass>();

	/**
	 * Process the Commit command and Start a work for it
	 * @param filename - the image name to be commited
	 * @param img - the content of the image
	 * @param sources - the source users composite the image
     */
	public void startCommit(String filename, byte[] img, String[] sources){

		/* Parse the request, log and start a new work */
		String logString = parseCommit(filename,sources);
		Helper.writeLog(PL,logName,logString);
		WorkClass wc = new WorkClass(PL,filename,img,sources);

		/* tasks in process */
		tasks.put(filename,wc);

		/* Start to process this work by new thread */
		TaskVote tv = new TaskVote(wc);
		tv.run();
	}

	/**
	 * Parse the commit parameters
	 * @param filename - the name of image need to be commited
	 * @param sources - the sources of the user images
     * @return - the log String which has the parameter information
     */
	public String parseCommit(String filename, String[] sources){
		/* All the paramteres are seplited by ";" */
		StringBuffer templog = new StringBuffer();
		templog.append("Commit;");
		templog.append(filename);

		for( String source : sources) {
			templog.append(";"+source);
		}
		return templog.toString();
	}

	/**
	 * Parse the log, and recovery the Work back to broken status
	 * @param logs - the logs read from the log file
     */
	public static void parseLog(List<String> logs){

		/* No log information when first start */
		if( logs==null ){
			return;
		}

		for(String str : logs){
			/* Read the Collage corresponding to log entry */
			String[] content = str.split(";");
			String status = content[0];
			String filename = content[1];
			WorkClass wc = null;

			/* If in the commit Status, construct the Work */
			if(status.equals(commitStatus)){
				String[] sources = Arrays.copyOfRange(content,2,content.length);
				wc = new WorkClass(PL,filename,null,sources);
				wc.status = commitStatus;
				tasks.put(filename,wc);
			}
			/* If in the decision Status, store the decision information*/
			else if(status.equals(decisionStatus)){
				if(tasks.containsKey(filename)){
					wc = tasks.get(filename);
					wc.status = decisionStatus;
					wc.decided = true;
					wc.decision = Boolean.parseBoolean(content[2]);
				}
			}
			/* If in the finish Status, remove from the working  map*/
			else if(status.equals(finishStatus)){
				if(tasks.containsKey(filename)){
					tasks.remove(filename);
				}
			}
		}

		/* Make the Work run again from the broken status */
		reRunTasks();
	}

	/**
	 * Make the Work run again from the broken status
	 */
	public static void reRunTasks(){
		WorkClass wc = null;
		for(String filename : tasks.keySet()){
			wc = tasks.get(filename);

			/* Breaks down at commit status, distribute No */
			if(wc.status.equals(commitStatus)){
				wc.decision = false;
				wc.distributeVote(false);
				wc.startAckTimer();
			}
			/* Break down at decision status, distribute decision*/
			else if(wc.status.equals(decisionStatus)){
				wc.distributeVote(wc.decision);
				wc.startAckTimer();
			}
		}
	}

	/**
	 * The main function to start the Server
	 * @param args - the port to communicate between server and user
	 * @throws Exception
     */
	public static void main ( String args[] ) throws Exception {
		if (args.length != 1) throw new Exception("Need 1 arg: <port>");
		Server srv = new Server();

		PL = new ProjectLib( Integer.parseInt(args[0]), srv );

		/* Read the log from the log file */
		List<String> logs = Helper.readLog(logName);
		/* Parse the log and recovery the status */
		parseLog(logs);


		/* Main loop, used to receive the responsed from users */
		while (true) {
			/* receive and parse the response */
			ProjectLib.Message msg = PL.getMessage();
			ImgMessage message = Helper.deserialize(msg.body);

			/* Work has been deleted when all ACK has been recieved */
			if(tasks.containsKey(message.filename)==false){
				continue;
			}

			/* retrieve the working work */
			WorkClass wc = tasks.get(message.filename);

			/* process message */
			if (wc.processMsg(message)==true) {
				tasks.remove(message.filename);
			}
		}
	}

	/**
	 * A runnable to start the Work constructed after commit command
	 */
	class TaskVote implements Runnable {
		WorkClass worker;

		public TaskVote(WorkClass worker) {
			this.worker = worker;
		}

		@Override
		public void run() {
			worker.startVote();
		}
	}


}

