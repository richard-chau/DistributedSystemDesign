/**
 * @author Haoyang Yuan (hangyangy)
 * Helper class to help process the collage. It can help
 * Serialize and Deserialize the message instances, read
 * and write out the log strings to log file.
 */
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/*** Helper.java ***/
public class Helper{

    /**
     * TO serilize the message instance to bytes
     * @param obj - the message instance
     * @return the serialized bytes
     */
    public static byte[] serialize(Object obj) {
        try(ByteArrayOutputStream b = new ByteArrayOutputStream()){
            try(ObjectOutputStream o = new ObjectOutputStream(b)){
                o.writeObject(obj);
            }catch (IOException e){

            }
            return b.toByteArray();
        } catch (IOException e) {
        }
        return null;
    }

    /**
     * TO deserilize the bytes to message instance
     * @param bytes - the hex bytes
     * @return the message instance
     */
    public static ImgMessage deserialize(byte[] bytes)  {
        try(ByteArrayInputStream b = new ByteArrayInputStream(bytes)){
            try(ObjectInputStream o = new ObjectInputStream(b)){
                return (ImgMessage)o.readObject();
            } catch (ClassNotFoundException e) {
            }
        } catch (IOException e) {
        }
        return null;
    }

    /**
     * To write down the content to corresponding log file
     * @param PL - the ProjectLib of the Server or USer
     * @param dest - the log file
     * @param content - the log string content
     */
    public static void writeLog(ProjectLib PL,String dest, String content) {
        try (BufferedWriter out =
                     new BufferedWriter(new FileWriter(dest, true))){
            out.write(String.format("%s\n", content));
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        PL.fsync();
    }

    /**
     * To read the log Strings from the file to ArrayList
     * @param dest - the log file
     * @return the Arraylist contains the log infomrmation
     */
    public static List<String> readLog(String dest){
		/* read log to the memory */
        List<String> strings = new ArrayList<String>();
        Scanner fileScanner = null;
        File file=new File(dest);
        if (file.exists()) {
            try {
                fileScanner = new Scanner(file);
                while (fileScanner.hasNext()) {
                    strings.add(fileScanner.next());
                }
                fileScanner.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return strings;
    }

}