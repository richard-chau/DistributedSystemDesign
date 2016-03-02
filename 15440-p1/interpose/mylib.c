/* 
 haoyangy - 15640 pro1
 Mylib.c is a library which can be invoked to manipulate
 system calls on the remote server through socket.
 
 Each time it connects, it will create a new connection.
 And to sends the parameters to remote server then 
 retieve the remote return values.
 
 Note: The file descriptor retrived from open operator
 is added with 10000 as offset to distinguish from local
 and remote.
 */

#define _GNU_SOURCE

#include <dlfcn.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdarg.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <errno.h>
#include <string.h>
#include <err.h>
#include "queue.h"
#include <assert.h>

/*opearation code macro*/
#define MYOPEN 1
#define MYWRITE 2
#define MYCLOSE 3
#define MYREAD 4
#define MYLSEEK 5
#define MYSTAT 6
#define MYUNLINK 7
#define MYGETEN 8
#define MYGETTREE 9
#define MYFREETREE 10

#define MAXMSGLEN 1000

/*Stop alignment*/
#pragma pack(0)

/*Define the packet header for all requests*/
typedef struct
{
    int opcode;
    int total_len;
    unsigned char data[0]; 
} request_header_t;

/*Define open header*/
typedef struct
{
    int flag;
    mode_t mode;
    int filename_len;
    unsigned char data[0];
} open_request_header_t;


/*Define gettree header*/
typedef struct
{
    int filename_len;
    unsigned char data[0];
} gettree_request_header_t;


/*Define stat header*/
typedef struct
{
    int ver;
    int filename_len;
    unsigned char data[0];
} stat_request_header_t;

/*Define write header*/
typedef struct
{
    int fd;
    size_t count;
    unsigned char data[0];
} write_request_header_t;

/*Define close header*/
typedef struct
{
    int fildes;
    unsigned char data[0];
} close_request_header_t;

/*Define unlink header*/
typedef struct
{
     int filename_len;
    unsigned char data[0];
} unlink_request_header_t;

/*Define lseek header*/
typedef struct
{
    int fildes;
    off_t offset;
    int whence;
    unsigned char data[0];
} lseek_request_header_t;

/*Define read header*/
typedef struct
{
    int fd;
    size_t count;
    unsigned char data[0];
} read_request_header_t;

/*Define getentry header*/
typedef struct
{
    int fd;
    size_t nbytes;
    off_t baseval;
    unsigned char data[0];
} getentry_request_header_t;



/*Define macro for the header sizes*/
#define HEADSIZE (sizeof(request_header_t))
#define OPENSIZE (sizeof(open_request_header_t))
#define WRITESIZE (sizeof(write_request_header_t))
#define CLOSESIZE (sizeof(close_request_header_t))
#define UNLINKSIZE (sizeof(unlink_request_header_t))
#define LSEEKSIZE (sizeof(lseek_request_header_t))
#define READSIZE (sizeof(lseek_request_header_t))
#define GETESIZE (sizeof(getentry_request_header_t))
#define STATSIZE (sizeof(stat_request_header_t))
#define GETTREESIZE (sizeof(gettree_request_header_t))

/*Define local and remote file descriptor offset*/
#define FDOFF 100000

/*Define some sizes of return structure*/
#define RSIZE (sizeof(int)*4+sizeof(char))
#define SSIZE (sizeof(struct stat))
#define OSIZE (sizeof(off_t))
#define BRSIZE (sizeof(int)*6)
#define NSIZE (sizeof(Node))
#define QSIZE (sizeof(Queue))
#define DTSIZE (sizeof(struct dirtreenode))

/*the helper function to implement communication*/
int _sendmsg(int sockfd,char* message,int len);
int _initconnect();
int _close(int sockfd);
char* _wait(int sessfd);
char* _waitlong(int sessfd);

/*The marshall functions to map parameters into packet headers*/
request_header_t* _marclose(int fildes);
request_header_t* _maropen(const char *pathname, int flags, mode_t m);
request_header_t* _marwrite(int fd, const void *buf, size_t count);
request_header_t* _marunlink(const char *path);
request_header_t* _marlseek(int fildes, off_t offset, int whence);
request_header_t* _marread(int fd, size_t count);
request_header_t* _marstat(int ver, const char * path);
request_header_t* _margetentry(int fd, size_t nbytes , off_t baseval);
request_header_t* _margettree(const char *path);

/*The covered original functions*/
int (*orig_open)(const char *pathname, int flags, ...);
int (*orig_close)(int fildes);
ssize_t (*orig_read)(int fd, void *buf, size_t count);
ssize_t (*orig_write)(int fd, const void *buf, size_t count);
off_t (*orig_lseek)(int fildes, off_t offset, int whence);
int (*orig_unlink)(const char *path);
ssize_t (*orig_getdirentries)(int fd, char *buf, size_t nbytes , off_t *basep);
int (*orig_stat)(int ver, const char * path, struct stat * stat_buf);

/*Tree functions linked from ../lib*/
struct dirtreenode* (*orig_getdirtree)( const char *path );
void (*orig_freedirtree)( struct dirtreenode* dt );


/*This is our replacement for the open function from libc.*/
int open(const char *pathname, int flags, ...) {
    request_header_t* header;
    open_request_header_t* payload;
    
    /* mode detection */
    mode_t m=0;
    if (flags & O_CREAT) {
        va_list a;
        va_start(a, flags);
        m = va_arg(a, mode_t);
        va_end(a);
    }
    /* marshall parameters */
	header = _maropen(pathname, flags, m);
    
    int sockfd = _initconnect();
   
    /* send packets */
    _sendmsg(sockfd,(char*)header,HEADSIZE);
    payload  = (open_request_header_t *)(header->data);
    _sendmsg(sockfd,(char*)header->data,header->total_len);
    
    /* wait for return values */
    char* ret = _wait(sockfd);
    
    _close(sockfd);
    int one = *((int*)ret);
    int two = *((int*)(ret+8));
    
    free(header);
    free(ret);
    
    /* add offset to the successful return values */
    if(one>0){
        one = one + FDOFF;
    }
    errno = two;
    return one;
}

/*This is our replacement for the close function from libc.*/
int close(int fildes) {
    /*Local or remote detection*/
    if(fildes<FDOFF){
        return orig_close(fildes);
    }
    fildes = fildes - FDOFF;
    
    request_header_t* header;
    close_request_header_t* payload;
    int sockfd = _initconnect();
    
    /*Marshall the parameters*/
    header = _marclose(fildes);
    
    payload  = (close_request_header_t *)(header->data);
    
    /*Send the request packets*/
    _sendmsg(sockfd,(char*)header,HEADSIZE);
    _sendmsg(sockfd,(char*)header->data,header->total_len);
    
    /*wait for return values*/
    char* ret = _wait(sockfd);
       _close(sockfd);
    int one = *((int*)ret);
    int two = *((int*)(ret+8));
    
    free(header);
    free(ret);
    
    errno = two;
    return one;
}

/*This is our replacement for the write function from libc.*/
ssize_t write(int fd, const void *buf, size_t count){
    /*Local or remote detection*/
    if(fd<FDOFF){
        return orig_write(fd,buf,count);
    }
    fd = fd - FDOFF;
    
    request_header_t* header;
    write_request_header_t* payload;
    int sockfd = _initconnect();
    
    /*Marshall the parameters*/
    header = _marwrite(fd,buf,count);
    payload  = (write_request_header_t *)(header->data);
    
    /*Send the header and opcode requests */
    _sendmsg(sockfd,(char*)header,HEADSIZE);
    _sendmsg(sockfd,(char*)header->data,header->total_len);
    
    /*wait for return values*/
    char* ret = _wait(sockfd);
    _close(sockfd);
    ssize_t one = *((ssize_t*)ret);
    int two = *((int*)(ret+8));
    
    free(header);
    free(ret);
    errno = two;
    return one;
}

/*This is our replacement for the read function from libc.*/
ssize_t read(int fd,  void *buf, size_t count){
    /*Local or remote detection*/
    if(fd<FDOFF){
        return orig_read(fd,buf,count);
    }
    fd = fd - FDOFF;
    
    request_header_t* header;
    read_request_header_t* payload;
    int sockfd = _initconnect();
    
    /*Marshall the parameters*/
    header = _marread(fd,count);
    
    payload  = (read_request_header_t *)(header->data);
    
    /*Send the header and opcode requests */
    _sendmsg(sockfd,(char*)header,HEADSIZE);
    _sendmsg(sockfd,(char*)header->data,header->total_len);
    
    
    /*wait for return values, errno and return data*/
    char* ret  = _waitlong(sockfd);
    _close(sockfd);
    
    int index = *((int*)ret);
    ssize_t one = *((ssize_t*)(ret+8));
    int two = *((int*)(ret+16));
    
    /*copy the return values to local and return*/
    if(one>0){
        memcpy(buf,ret+24,index-16);
    }
    
    free(header);
    free(ret);
    errno = two;
    return one;

}

/*This is our replacement for the lseek function from libc.*/
off_t lseek(int fildes, off_t offset, int whence){
    /*Local or remote detection*/
    if(fildes<FDOFF){
        return orig_lseek(fildes,offset,whence);
    }
    fildes = fildes - FDOFF;
    
    request_header_t* header;
    lseek_request_header_t* payload;
    int sockfd = _initconnect();
    
    /*Marshall the parameters*/
    header = _marlseek(fildes,offset,whence);
    
    payload  = (lseek_request_header_t *)(header->data);
    
    /*Send the header and opcode requests */
    _sendmsg(sockfd,(char*)header,HEADSIZE);
    _sendmsg(sockfd,(char*)header->data,header->total_len);
    
    /*wait for return values, errno*/
    char* ret = _wait(sockfd);
    _close(sockfd);
    off_t one = *((off_t*)ret);
    int two = *((int*)(ret+8));
    
    free(header);
    free(ret);
    errno = two;
    return one;
}

/*This is our replacement for the unlink function from libc.*/
int unlink(const char *path){
    request_header_t* header;
    unlink_request_header_t* payload;
    int sockfd = _initconnect();
    
    /*Marshall the parameters*/
    header = _marunlink(path);
    
    payload  = (unlink_request_header_t *)(header->data);
    
    /*Send the header and opcode requests */
    _sendmsg(sockfd,(char*)header,HEADSIZE);
    _sendmsg(sockfd,(char*)header->data,header->total_len);
    
    /*wait for return values, errno*/
    char* ret = _wait(sockfd);
    _close(sockfd);
    int one = *((int*)ret);
    int two = *((int*)(ret+8));
    
    free(header);
    free(ret);
    errno = two;
    return one;
}

/*This is our replacement for the getentries function from libc.*/
ssize_t getdirentries(int fd, char *buf, size_t nbytes, off_t *basep){
     /*Local or remote detection*/
    if(fd<FDOFF){
        return orig_getdirentries(fd,buf,nbytes,basep);
    }
    fd = fd - FDOFF;
    
    request_header_t* header;
    getentry_request_header_t* payload;
    int sockfd = _initconnect();
    
     /*Marshall the parameters*/
    off_t baseval = *(basep);
    header = _margetentry(fd,nbytes,baseval);
    
    payload  = (getentry_request_header_t *)(header->data);
    
    /*Send the header and opcode requests */
    _sendmsg(sockfd,(char*)header,HEADSIZE);
    _sendmsg(sockfd,(char*)header->data,header->total_len);
    
    /*wait for return values, errno*/
    char* ret = _waitlong(sockfd);
    _close(sockfd);
    int index = *((int*)ret);
    
    ssize_t one = *((ssize_t*)(ret+8));
    int two = *((int*)(ret+16));
    baseval = *(off_t*)(ret+24);
    *basep = baseval;
    
    /*copy the return values to local and return*/
    if(one>0){
        memcpy(buf,ret+32,index-24);
    }
    
    free(header);
    free(ret);
    errno = two;
    
    return one;
}

/*This is our replacement for the stat function from libc.*/
int __xstat(int ver, const char * path, struct stat * stat_buf){
    request_header_t* header;
    stat_request_header_t* payload;
    int sockfd = _initconnect();

    /*Marshall the parameters*/
    header = _marstat(ver,path);
    
    payload = (stat_request_header_t *)(header->data);
    
    /*Send the header and opcode requests */
    _sendmsg(sockfd,(char*)header,HEADSIZE);
    _sendmsg(sockfd,(char*)header->data,header->total_len);
    
    /*wait for return values, errno, and datas*/
    char* ret = _waitlong(sockfd);
    _close(sockfd);
    
    int index = *((int*)ret);
    int one = *((int*)(ret+8));
    int two = *((int*)(ret+16));

    /*copy the return values to local and return*/
    memcpy(stat_buf,ret+24,index-16);
    
    free(header);
    free(ret);
    errno = two;
    return one;

}

/*This is our replacement for the getdirtree function from libc.*/
struct dirtreenode* getdirtree( const char *path ){
    request_header_t* header;
    gettree_request_header_t* payload;
    int sockfd = _initconnect();

     /*Marshall the parameters*/
    header = _margettree(path);
    
    payload  = (gettree_request_header_t *)(header->data);
    
    /*Send the header and opcode requests */
    _sendmsg(sockfd,(char*)header,HEADSIZE);
    _sendmsg(sockfd,(char*)header->data,header->total_len);
    
    /*wait for return values, errno, and datas*/
    char* ret = _waitlong(sockfd);
    _close(sockfd);
    
    /*process deserilize tree*/
    int totoallen = *((int*)ret);
    int two = *((int*)(ret+8));
    int index = 16;
    
    /*initialize the tree*/
    Queue *q = init();
    struct dirtreenode* first = (struct dirtreenode*) malloc (DTSIZE);
    Enqueue(q, first);

    int len;
    char* thisname;
    int i;
    struct dirtreenode ** sons;
    struct dirtreenode* node;
    struct dirtreenode* newnode;
    
    /*When the queue is not empty*/
    while(q->size!=0){
        /*get the front node in the queue*/
        node = q->front->this;
      
        /*fill the node information to the first node*/
        len = *((int*)(ret+index));
        index = index + sizeof(int);
        
        thisname = (char*) malloc(len);
        memcpy(thisname,ret+index,len);
        node->name = thisname;
        index= index + len;
        
        node->num_subdirs = *((int*)(ret+index));
        
        index = index+sizeof(int);
        
        /*Create pointers for sons*/
        if(node->num_subdirs>0){
            sons = malloc( node->num_subdirs*sizeof(struct dirtreenode *) );
            node->subdirs = sons;
        }else{
            node->subdirs = NULL;
        }
        
        /*Create empty node and queue them*/
        i=0;
        for(i;i<node->num_subdirs;i++){
            newnode = (struct dirtreenode*) malloc (DTSIZE);
            sons[i] = newnode;
            Enqueue(q,newnode);
        }
        /*One node is finished process*/
        Dequeue(q);
    }
    
    free(header);
    free(ret);
    free(q);
    errno = two;
    return first;
}

/*This is our replacement for the freedirtree function*/
void freedirtree( struct dirtreenode* dt ){
    return orig_freedirtree(dt);
}


/*automatically called when program is started*/
void _init(void) {
    /*Initialize and replace all functions, store the original ones*/
	orig_open = dlsym(RTLD_NEXT, "open");
    orig_stat = dlsym(RTLD_NEXT, "__xstat");
    orig_close = dlsym(RTLD_NEXT, "close");
    orig_read = dlsym(RTLD_NEXT, "read");
    orig_write = dlsym(RTLD_NEXT, "write");
    orig_lseek = dlsym(RTLD_NEXT, "lseek");
    orig_unlink = dlsym(RTLD_NEXT, "unlink");
    orig_getdirentries = dlsym(RTLD_NEXT, "getdirentries");
    orig_getdirtree = dlsym(RTLD_NEXT, "getdirtree");
    orig_freedirtree = dlsym(RTLD_NEXT,"freedirtree");
    
    fprintf(stderr, "Init mylib\n");
}

/*Start a socket connection*/
int _initconnect(){
    char *serverip;
    char *serverport;
    unsigned short port;
    int sockfd, rv;
    struct sockaddr_in srv;
    
    // Get environment variable indicating the ip address of the server
    serverip = getenv("server15440");
    if (serverip) {
        fprintf(stderr,"Got environment variable server15440: %s\n", serverip);
    }
    
    else {
        fprintf(stderr,
            "Environment variable server15440 not found.  Using 127.0.0.1\n");
        serverip = "127.0.0.1";
    }
    
    // Get environment variable indicating the port of the server
    serverport = getenv("serverport15440");
    if (serverport) fprintf(stderr,
            "Got environment variable serverport15440: %s\n", serverport);
    else {
        fprintf(stderr,
            "Environment variable serverport15440 not found.  Using 12333\n");
        serverport = "13888";
    }
    port = (unsigned short)atoi(serverport);
    
    // Create socket
    sockfd = socket(AF_INET, SOCK_STREAM, 0);	// TCP/IP socket
    if (sockfd<0) err(1, 0);			// in case of error
    
    // setup address structure to point to server
    memset(&srv, 0, sizeof(srv));			// clear it first
    srv.sin_family = AF_INET;			// IP family
    srv.sin_addr.s_addr = inet_addr(serverip);	// IP address of server
    srv.sin_port = htons(port);			// server port
    
    // actually connect to the server
    rv = connect(sockfd, (struct sockaddr*)&srv, sizeof(struct sockaddr));
    if (rv<0) err(1,0);

    return sockfd;
}

/*Marshall the open parameters to header*/
request_header_t* _maropen(const char *pathname, int flags, mode_t m){
    
    int lenno0 = strlen(pathname);
    int total_len = OPENSIZE+lenno0+1;
    
    /*create the header size*/
    void *buffer = malloc(total_len+HEADSIZE);
    request_header_t *header = buffer;
    open_request_header_t *open_header = header->data;
    header->opcode = MYOPEN;
    
    
    open_header->mode = m;
    char *open_filename = open_header->data;
    
    /*fill in the header information*/
    header->total_len = total_len;
    open_header-> filename_len = lenno0+1;
    open_header->flag = flags;
    memcpy(open_filename, pathname,open_header-> filename_len);
    open_filename[lenno0] = 0;
    
    return header;
}

/*Marshall the read parameters to header*/
request_header_t* _marread(int fd, size_t count){
    int total_len = READSIZE;
    /*create the header size*/
    void *buffer = malloc(total_len+HEADSIZE);
    
    /*fill in the header information*/
    request_header_t *header = buffer;
    read_request_header_t *read_header = header->data;
    header->opcode = MYREAD;
    
    header->total_len = total_len;
    
    read_header->fd = fd;
    read_header->count = count;
    return header;

}

/*Marshall the close parameters to header*/
request_header_t* _marclose(int fildes){
    int total_len = CLOSESIZE;
    /*create the header size*/
    void *buffer = malloc(total_len+HEADSIZE);
    
    /*fill in the header information*/
    request_header_t *header = buffer;
    close_request_header_t *close_header = header->data;
    header->opcode = MYCLOSE;
    
    header->total_len = total_len;

    close_header->fildes = fildes;
    return header;
}

/*Marshall the lseek parameters to header*/
request_header_t* _marlseek(int fildes, off_t offset, int whence){
    int total_len = LSEEKSIZE;
    /*create the header size*/
    void *buffer = malloc(total_len+HEADSIZE);
    
    /*fill in the header information*/
    request_header_t *header = buffer;
    lseek_request_header_t *lseek_header = header->data;
    header->opcode = MYLSEEK;
    
    header->total_len = total_len;
    
    lseek_header->fildes = fildes;
    lseek_header->offset = offset;
    lseek_header->whence = whence;
    
    return header;

}

/*Marshall the write parameters to header*/
request_header_t* _marwrite(int fd, const void *buf, size_t count){
    int total_len = WRITESIZE+count;
    /*create the header size*/
    void *buffer = malloc(total_len+HEADSIZE);
    
    /*fill in the header information*/
    request_header_t *header = buffer;
    write_request_header_t *write_header = header->data;
    header->opcode = MYWRITE;
    
    header->total_len = total_len;
    
    write_header->fd = fd;
    write_header->count = count;
    
    void *writebuf = write_header->data;
    memcpy(writebuf, buf,write_header->count);
    
    return header;
}

/*Marshall the unlink parameters to header*/
request_header_t* _marunlink(const char *path){
    int lenno0 = strlen(path);
    /*create the header size*/
    int total_len = UNLINKSIZE+lenno0+1;
    void *buffer = malloc(total_len+HEADSIZE);
    
    /*fill in the header information*/
    request_header_t *header = buffer;
    unlink_request_header_t *unlink_header = header->data;
    header->opcode = MYUNLINK;
    
    char *unlink_filename = unlink_header->data;
    
    header->total_len = total_len;
    unlink_header-> filename_len = lenno0+1;
    memcpy(unlink_filename, path,unlink_header-> filename_len);
    unlink_filename[lenno0] = 0;

    return header;
}

/*Marshall the gettree parameters to header*/
request_header_t* _margettree(const char *path){
    int lenno0 = strlen(path);
    int total_len = GETTREESIZE+lenno0+1;
    
    /*create the header size*/
    void *buffer = malloc(total_len+HEADSIZE);
    
    /*fill in the header information*/
    request_header_t *header = buffer;
    gettree_request_header_t *gettree_header = header->data;
    header->opcode = MYGETTREE;
    
    char *gettree_filename = gettree_header->data;
    
    header->total_len = total_len;
    gettree_header-> filename_len = lenno0+1;
    memcpy(gettree_filename, path,gettree_header-> filename_len);
    gettree_filename[lenno0] = 0;
    
    return header;
}

/*Marshall the getentry parameters to header*/
request_header_t* _margetentry(int fd, size_t nbytes , off_t baseval){
    int total_len = GETESIZE;
    /*create the header size*/
    void *buffer = malloc(total_len+HEADSIZE);
    
    /*fill in the header information*/
    request_header_t *header = buffer;
    getentry_request_header_t *gete_header = header->data;
    header->opcode = MYGETEN;
    
    header->total_len = total_len;
    
    gete_header->fd = fd;
    gete_header->nbytes = nbytes;
    gete_header->baseval = baseval;
    
    return header;
}

/*Marshall the stat parameters to header*/
request_header_t* _marstat(int ver, const char * path){
    int lenno0 = strlen(path);
    int total_len = OPENSIZE+lenno0+1;
    
    /*create the header size*/
    void *buffer = malloc(total_len+HEADSIZE);
    
    /*fill in the header information*/
    request_header_t *header = buffer;
    stat_request_header_t *stat_header = header->data;
    header->opcode = MYSTAT;
    
    stat_header->ver = ver;
    char *stat_filename = stat_header->data;
    
    header->total_len = total_len;
    stat_header-> filename_len = lenno0+1;

    memcpy(stat_filename, path,stat_header-> filename_len);
    stat_filename[lenno0] = 0;
    
    return header;
}

/*Wait for the return values from server*/
char* _wait(int sessfd){
    int  rv;
    char buf[MAXMSGLEN];
    
    /*Receive the data from connection socket*/
    while ( (rv=recv(sessfd, buf, RSIZE, 0)) > 0) {
        buf[rv]=0;		// null terminate string to print
        
        /*Copy the return value from buffer to heap space*/
        char* payload = (char*) malloc (RSIZE);
        memcpy(payload, buf, RSIZE);
   
        return payload;
    }
}

/*Wait for the return values and extra data from server*/
char* _waitlong(int sessfd){
    int  rv;
    char buf[MAXMSGLEN];
    int length;
    int rvcount=0;
    char* payload;
    int first = 0;
    
    /*Receive the data from connection socket*/
    while ( (rv=recv(sessfd, buf, MAXMSGLEN, 0)) > 0) {
        /*malloc space for store data when receive the length*/
        if(first==0){
            first = 1;
            length = *((int*)buf);
            payload = (char*) malloc ( length );
        }
        
        /*Copy the return value from buffer to heap space*/
        memcpy(payload+rvcount,buf,rv);
        rvcount = rvcount + rv;
        if(rvcount==length){
            break;
        }
    }
    return payload;
}

/*Send message to the server*/
int _sendmsg(int sockfd, char* msg,int len) {
    return send(sockfd, msg, len, 0);
}

/*Original close the socket*/
int _close(int sockfd){
    orig_close(sockfd);
    return 0;
}


