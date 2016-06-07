/**
 * @author Haoyang Yuan (hangyangy)
 * The message class contains all the message sends out and
 * received from
 */
import java.io.Serializable;
import java.util.*;

/*** ImgMessage.java ***/
public class ImgMessage implements Serializable {

    /* The information contained in the message class*/
    public int type = 1;
    public String filename = null;
    public String user = null;
    public LinkedList<String> imgele = new LinkedList<>();
    public byte[] img = null;
    public boolean vote = false;

    /**
     * Constructor to construct the message from user to Server
     * @param _type - the type of message
     * @param _filename - the name of collage
     * @param _user - the source of the message
     * @param _vote - the voting result
     */
    public ImgMessage(int _type, String _filename,
                      String _user, boolean _vote) {
        this.type = _type;
        this.user = _user;
        this.filename = _filename;
        this.vote = _vote;
    }

    /**
     * Constructor to construct the message from Server to User
     * @param _type - the type of the message
     * @param _filename - the name of the collage
     * @param _user - the user who receives the message
     * @param _imgele - the source images of collage
     * @param _img - the collage image content
     */
    public ImgMessage(int _type, String _filename, String _user,
                      String _imgele, byte[] _img) {
        this.type = _type;
        this.filename = _filename;
        this.user = _user;
        this.imgele.add(_imgele);
        this.img = _img;
    }

    /**
     * Add the source image to the source image set of collage
     * @param _imgele - the source image
     */
    public void addEle(String _imgele){
        if(imgele.contains(_imgele)==false) {
            this.imgele.add(_imgele);
        }
    }
}