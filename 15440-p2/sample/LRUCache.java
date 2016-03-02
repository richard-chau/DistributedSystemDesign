import java.util.HashMap;
import java.util.Map;

/**
 * A LRU Cache implemented by hashmap and LinkedList
 * Modified from my former LeetCode submissions
 * 
 * @author haoyangy
 */

/*** LRUCache.java ***/
public class LRUCache {
	
	/*A file Node in the cache*/
    public class Node{
    	
    	
    	public Node pre;
    	public Node next;    
    	public int version;
    	public String path;
    	public long size;
        
    	/**
    	 * create file node
    	 * @param path - file path
    	 * @param size - the file size
    	 */
        public Node (String path, long size){
            this.path = path;
            this.size = size;
        }

        /**
    	 * remove the node from linkedlist
    	 */
        public void remove(){
            this.pre.next = this.next;
            this.next.pre = this.pre;
        }
        
        /**
    	 * add node to the front of linkedlist
    	 * @param front - the node position to insert
    	 */
        public void addbehind(Node front){
            this.next = front.next;
            this.pre = front;
            front.next = this;
            this.next.pre = this;
        }
    }
    
    /*Cache information*/
    public long cap;
    public long cnt;
    public Node head;
    public Node tail;
    public Map<String,Node> map = new HashMap();
    
    /**
	 * create LRUCache
	 * @param cachesize - the total size of cache
	 */
    public LRUCache(long cachesize) {
        cnt = 0;
        cap = cachesize;
        head = new Node(null,0);
        tail = new Node(null,0);
        head.next = tail;
        tail.pre = head;
    }
    
    /**
	 * get the last node need to be evicted
	 * @return the last node
	 */
    public Node getlast(){
        return tail.pre;
    }
    
    /**
	 * get the available cache size
	 * @return the available cache size
	 */
    public long getremain(){
    	return cap - cnt;
    }
    
    /**
   	 * get the next LRU node from the node position
   	 * @param node - the current node
   	 * @return the node after current node
   	 */
    public Node getnext(Node node){
    	if(node==null){
    		if(getlast()==head){
    			return null;
    		}
    		return getlast();
    	}
    	else if(node.pre!=head){
    		return node.pre;
    	}
    	return null;
    }
    
    /**
   	 * move the current node to the first position in list
   	 * @param the current node 
   	 */
    public void movefront(Node temp){
        temp.remove();
        temp.addbehind(head);
    }
    
    /**
   	 * update the node and move it to first position
   	 * @param path - the path of the file, corresponding a node
   	 */
    public void update(String path) {
        if(map.containsKey(path)){
            movefront(map.get(path));
            
        }
    }
    
    /**
   	 * check if cache contains this file
   	 * @param path - the name of the file
   	 * @return true if the cache contains file, false if not
   	 */
    public boolean contains(String path) {
        if(map.containsKey(path)){
            
            return true;
        }
        else{
            return false;
        }
    }
    
    /**
   	 * remove the file from the cache
   	 * @param path - the name of the file
   	 * @return true if the cache removes file, false if not
   	 */
    public boolean removebypath(String path){
    	if(map.containsKey(path)){
    		Node node = map.get(path);
        	cnt = cnt - node.size;
    		node.remove();
    		map.remove(node.path);
    		return true;
    	}
    	return false;
    }
    
    /**
   	 * remove the file from the cache
   	 * @param node - the node instance contains the file
   	 * @return true if the cache removes file, false if not
   	 */
    public boolean remove(Node node){
    	if(map.containsKey(node.path)){
        	cnt = cnt - node.size;
    		node.remove();
    		map.remove(node.path);
    		return true;
    	}
    	return false;
    }
    
    /**
   	 * set the file into cache, and create node for it
   	 * @param path - the name of the file
   	 * @param size - the size of the file
   	 */
    public void set(String path, long size) {
    	if(map.containsKey(path)){
            Node temp = map.get(path);
            movefront(temp);
            cnt = cnt - temp.size;
            temp.size = size;
            cnt = cnt + temp.size;
        }
        else{
        	cnt += size;
        	Node temp = new Node(path,size);
        	temp.addbehind(head);
        	map.put(path,temp);
        }
    }
}