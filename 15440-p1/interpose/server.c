/*
 haoyangy - 15640 pro1
 Server.c is a library which can be invoked to manipulate
 system calls on the remote server through socket.
 
 Each time the server gets a connection, it will parse the 
 request and then invoke local functions to execute. After
 execution, it will send back the return value, errno and 
 maybe the extra data
 
 Note: The file descriptor retrived from open operator
 is added with 10000 as offset to distinguish from local
 and remote.
 */

#include <stdio.h>
#include <stdlib.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <string.h>
#include <unistd.h>
#include <err.h>
#include <errno.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dirent.h>
#include "queue.h"
#include <pthread.h> 

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
#pragma pack(0)

/*Define the packet header for all requests*/
typedef struct
{
    int opcode;
    int total_len;
    unsigned char data[0];
} request_header_t;

/*Define the open header*/
typedef struct
{
    int flag;
    mode_t mode;
    int filename_len;
    unsigned char data[0];
} open_request_header_t;

/*Define the stat header*/
typedef struct
{
    int ver;
    int filename_len;
    unsigned char data[0];
} stat_request_header_t;

/*Define the gettree header*/
typedef struct
{
    int filename_len;
    unsigned char data[0];
} gettree_request_header_t;

/*Define the write header*/
typedef struct
{
    int fd;
    size_t count;
    unsigned char data[0];
} write_request_header_t;

/*Define the close header*/
typedef struct
{
    int fildes;
    unsigned char data[0];
} close_request_header_t;

/*Define the unlink header*/
typedef struct
{
    int filename_len;
    unsigned char data[0];
} unlink_request_header_t;

/*Define the lseek header*/
typedef struct
{
    int fildes;
    off_t offset;
    int whence;
    unsigned char data[0];
} lseek_request_header_t;

/*Define the read header*/
typedef struct
{
    int fd;
    size_t count;
    unsigned char data[0];
} read_request_header_t;

/*Define the getentry header*/
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

/*Define some sizes of return structure*/
#define RSIZE (sizeof(int)*4+sizeof(char))
#define SSIZE (sizeof(struct stat))
#define OSIZE (sizeof(off_t))
#define BRSIZE (sizeof(int)*6)
#define NSIZE (sizeof(Node))
#define QSIZE (sizeof(Queue))
#define DTSIZE (sizeof(struct dirtreenode))

/*The unmarshall functions to parse packet headers, and make up requests data*/
char* _seropen(open_request_header_t* openhead,int sessfd);
char* _serclose(close_request_header_t* closehead,int sessfd);
char* _serwrite(write_request_header_t* writehead,int sessfd);
char* _serread(read_request_header_t* writehead,int sessfd);
char* _serlseek(lseek_request_header_t* writehead,int sessfd);
char* _serstat(stat_request_header_t* writehead,int sessfd);
char* _serunlink(unlink_request_header_t* writehead,int sessfd);
char* _sergetentry(getentry_request_header_t* writehead,int sessfd);
char* _sergettree(gettree_request_header_t* gettreehead,int sessfd);
void* serverprocess(void *vargp);

/* The main function is listenning the socket connection */
int main(int argc, char**argv) {
    int sockfd, rv;
	char *serverport;
	unsigned short port;
	
	struct sockaddr_in srv, cli;
	socklen_t sa_size;
    int* sessfd;
    // Get environment variable indicating the port of the server
	serverport = getenv("serverport15440");
	if (serverport) port = (unsigned short)atoi(serverport);
	else port=13888;
	
	// Create socket
	sockfd = socket(AF_INET, SOCK_STREAM, 0);	// TCP/IP socket
	if (sockfd<0) err(1, 0);			// in case of error
	
	// setup address structure to indicate server port
	memset(&srv, 0, sizeof(srv));			// clear it first
	srv.sin_family = AF_INET;			// IP family
	srv.sin_addr.s_addr = htonl(INADDR_ANY);	// don't care IP address
	srv.sin_port = htons(port);			// server port

	// bind to our port
	rv = bind(sockfd, (struct sockaddr*)&srv, sizeof(struct sockaddr));
	if (rv<0) err(1,0);
	
	// start listening for connections
	rv = listen(sockfd, 5);
	if (rv<0) err(1,0);
	sa_size = sizeof(struct sockaddr_in);
    pthread_t tid;
    while (1){
        
        // Wait for new socket and open a new thread to process
        sessfd = (int *)malloc(sizeof(int));
        (*sessfd) = accept(sockfd, (struct sockaddr *)&cli, &sa_size);
         if (sessfd<0) err(1,0);
        
        /*Create the thread*/
        pthread_create(&tid, NULL, serverprocess, (void*)sessfd);
    }
    
	close(sockfd);
	return 0;
}

/*Main thread function to process the socket connection*/
void* serverprocess(void *vargp){

        char buf[MAXMSGLEN+1];
        int  rv;
        int sessfd = *((int *)vargp);
        pthread_detach(pthread_self());
    
        /*Free the previous malloced socketfd*/
        free(vargp);
    
        /*Wait for new packet the header packet*/
        while ( (rv=recv(sessfd, buf, HEADSIZE, 0)) > 0) {
            buf[rv]=0;		// null terminate string to print
            fprintf(stderr,"\n");
            
            /* Malloc the header to store header from buffer*/
            request_header_t *head = (request_header_t*)malloc(HEADSIZE);
            memcpy(head, buf, HEADSIZE);
            int len2 = head->total_len;
            
            fprintf(stderr,"Header opcode: %d, total_len:%d\n",
                    head->opcode,head->total_len);
            
            /* Malloc the payload from heap according to the header request */
            void* payload = (void*) malloc (len2);
            
            /* Recieve the payload data*/
            int rvcount=0;
            while( (rv=recv(sessfd, buf, MAXMSGLEN, 0)) > 0  ){
                memcpy(payload+rvcount,buf,rv);
                rvcount = rvcount + rv;
                if(rvcount==len2){
                    break;
                }
            }
            
            /* Process each kind of request header*/
            char* ret;
            errno = 0;
            if(head->opcode==MYOPEN){
                open_request_header_t* openhead = payload;
                ret = _seropen(openhead,sessfd);
            }
            else if(head->opcode==MYCLOSE){
                close_request_header_t* closehead = payload;
                ret = _serclose(closehead,sessfd);
            }
            else if(head->opcode==MYWRITE){
                write_request_header_t* writehead = payload;
                ret = _serwrite(writehead,sessfd);
            }
            else if(head->opcode==MYREAD){
                read_request_header_t* readhead = payload;
                ret = _serread(readhead,sessfd);
            }
            else if(head->opcode==MYSTAT){
                stat_request_header_t* stathead = payload;
                ret = _serstat(stathead,sessfd);
            }
            else if(head->opcode==MYLSEEK){
                lseek_request_header_t* lseekhead = payload;
                ret = _serlseek(lseekhead,sessfd);
            }
            else if(head->opcode==MYGETEN){
                getentry_request_header_t* getehead = payload;
                ret = _sergetentry(getehead,sessfd);
            }
            else if(head->opcode==MYUNLINK){
                unlink_request_header_t* unlinkhead = payload;
                ret = _serunlink(unlinkhead,sessfd);
            }
            else if(head->opcode==MYGETTREE){
                gettree_request_header_t* gettreehead = payload;
                ret = _sergettree(gettreehead,sessfd);
            }
            
            /* Free the malloced data and return*/
            free(ret);
            free(payload);
            free(head);
        }
        
        // either client closed connection, or error
        if (rv<0) err(1,0);
        close(sessfd);
    

}

/* Parse the open header and send back the reponse*/
char* _seropen(open_request_header_t* openhead,int sessfd){
    
    /*Read the parameters from header*/
    int flag = openhead->flag;
    mode_t mode = openhead->mode;
    char* filename = openhead->data;
    
    /* Malloc response*/
    char* result = (char*)malloc(RSIZE);
    
    /* Process according to parameters*/
    int ret = open(filename, flag,mode);
    
    /* Marshall and send back the response*/
    *((int*)result) = ret;
    *((int*)(result+8)) = errno;
    *(result+16) = 0;
    send(sessfd, result, RSIZE , 0);
    
    return result;
}

/* Parse the close header and send back the reponse*/
char* _serclose(close_request_header_t* closehead,int sessfd){
    /*Read the parameters from header*/
    int fildes = closehead->fildes;
    
    /* Malloc response*/
    char* result = (char*)malloc(RSIZE);
    
    /* Process according to parameters*/
    int ret = close(fildes);
    
    /* Marshall and send back the response*/
    *((int*)result) = ret;
    *((int*)(result+8)) = errno;
    *(result+16) = 0;
    send(sessfd, result, RSIZE , 0);
    
    return result;
}

/* Parse the write header and send back the reponse*/
char* _serwrite(write_request_header_t* writehead,int sessfd){
     /*Read the parameters from header*/
    int fd = writehead->fd;
    size_t count = writehead->count;
    char *buf = writehead->data;
    
    /* Malloc response*/
    char* result = (char*)malloc(RSIZE);
    
    /* Process according to parameters*/
    ssize_t ret = write(fd,buf,count);
    
    /* Marshall and send back the response*/
    *((ssize_t*)result) = ret;
    *((int*)(result+8)) = errno;
    *(result+16) = 0;
    send(sessfd, result, RSIZE , 0);
    
    return result;
}

/* Parse the read header and send back the reponse*/
char* _serread(read_request_header_t* readhead,int sessfd){
     /*Read the parameters from header*/
    int fd = readhead->fd;
    size_t count = readhead->count;
    char* buf = (char*) malloc (count);
    
    /* Process according to parameters*/
    ssize_t retsize = read(fd,buf,count);
    int temperr = errno;
    
    /* Marshall and send back the response*/
    char* result;
    /*If the read returns false then response standard header*/
    if(retsize<0){
        
        /* Malloc response*/
        result = (char*)malloc(BRSIZE);
        *((int*)result) = BRSIZE;
        *((ssize_t*)(result+8)) = retsize;
        *((int*)(result+16)) = temperr;
        send(sessfd, result, BRSIZE, 0);
        
    /*If the read returns ttrue then response headers according to size*/
    }else{
        
        /* Malloc response*/
        result = (char*)malloc(BRSIZE+retsize);
        
        *((int*)result) = BRSIZE+retsize;
        *((ssize_t*)(result+8)) = retsize;
        *((int*)(result+16)) = temperr;
        memcpy(result+24,buf,retsize);
        send(sessfd, result, BRSIZE+retsize, 0);
        
    }
    
    free(buf);
    return result;
}

/* Parse the stat header and send back the reponse*/
char* _serstat(stat_request_header_t* stathead,int sessfd){
    /*Read the parameters from header*/
    int ver = stathead->ver;
    int filename_len = stathead->filename_len;
    char* filename = stathead->data;

     /* Malloc response*/
    struct stat* restat  = (struct stat *) malloc (SSIZE);
    char* result = (char*)malloc(BRSIZE+SSIZE);
    
    /* Process according to parameters*/
    int ret = __xstat(ver, filename, restat);

    /* Marshall and send back the response*/
    *((int*)result) = BRSIZE+SSIZE;

    *((int*)(result+8)) = ret;
    *((int*)(result+16)) = errno;
    memcpy(result+24,restat,SSIZE);
    send(sessfd, result, BRSIZE+SSIZE, 0);
    
    free(restat);
    return result;
}

/* Parse the lseek header and send back the reponse*/
char* _serlseek(lseek_request_header_t* lseekhead,int sessfd){
    /*Read the parameters from header*/
    int fildes = lseekhead->fildes;
    off_t offset = lseekhead->offset;
    int whence = lseekhead->whence;
    
    /* Malloc response*/
    char* result = (char*)malloc(RSIZE);
    
    /* Process according to parameters*/
    off_t ret = lseek(fildes, offset, whence);
    
    /* Marshall and send back the response*/
    *((off_t*)result) = ret;
    *((int*)(result+8)) = errno;
    send(sessfd, result, RSIZE, 0);
    return result;
}

/* Parse the unlink header and send back the reponse*/
char* _serunlink(unlink_request_header_t* unlinkhead,int sessfd){
    /*Read the parameters from header*/
    int filename_len = unlinkhead->filename_len;
    char* filename = unlinkhead->data;
    
    /* Malloc response*/
    char* result = (char*)malloc(RSIZE);
    
    /* Process according to parameters*/
    int ret = unlink(filename);
    
    /* Marshall and send back the response*/
    *((int*)result) = ret;
    *((int*)(result+8)) = errno;
    send(sessfd, result, RSIZE, 0);
    return result;
}

/* Parse the getentry header and send back the reponse*/
char* _sergetentry(getentry_request_header_t* getehead,int sessfd){
    /*Read the parameters from header*/
    int fd = getehead->fd;
    size_t nbytes = getehead->nbytes;
    off_t baseval = getehead->baseval;
    
    
    char* buf = (char*) malloc(nbytes);
    off_t* basep = (off_t*) malloc(OSIZE);
    *basep = baseval;
   
    /* Process according to parameters*/
    ssize_t ret = getdirentries(fd, buf, nbytes , basep);
    int temperr = errno;
   

    /* Detect if the process is successful*/
    char* result;
    if(ret<0){
         /* Malloc response*/
        result = (char*)malloc(BRSIZE+OSIZE);
        
        /* Marshall and send back the response*/
        *((int*)result) = BRSIZE+OSIZE;
        *((ssize_t*)(result+8)) = ret;
        *((int*)(result+16)) = temperr;
        *((off_t*)(result+24)) = *basep;
        
        send(sessfd, result, BRSIZE+OSIZE, 0);
    }
    else{
         /* Malloc response*/
        result = (char*)malloc(BRSIZE+OSIZE+ret);
        
        /* Marshall and send back the response*/
        *((int*)result) = BRSIZE+OSIZE+ret;
        *((ssize_t*)(result+8)) = ret;
        *((int*)(result+16)) = temperr;
        *((off_t*)(result+24)) = *basep;
        memcpy(result+32,buf,ret);

        send(sessfd, result, BRSIZE+OSIZE+ret, 0);
    }
    
    free(basep);
    free(buf);
    return result;

}

/* Parse the gettree header and send back the reponse*/
char* _sergettree(gettree_request_header_t* gettreehead,int sessfd){
    /*Read the parameters from header*/
    int filename_len = gettreehead->filename_len;
    char* filename = gettreehead->data;
    int i = 0;
    int sons = 0;
    
    /* Process according to parameters*/
    struct dirtreenode* head = getdirtree( filename);
    int temperr = errno;
    int totallen = 0;
    
    /*Initialize the empty queue*/
    Queue *q = init();
    Enqueue(q, head);
    
    /*Tranverse the tree to count the length*/
    while(q->size!=0){
        
        /*Get the front node*/
        struct dirtreenode* node= q->front->this;
        sons = node->num_subdirs;
        
        /*Add all the length to count*/
        totallen = totallen +  sizeof(int);
        totallen = totallen + strlen(node->name) + 1;
        totallen = totallen +  sizeof(int);
        
        /*Store the sons nodes to the queue*/
        i = 0;
        for(i;i<sons;i++){
            Enqueue(q,node->subdirs[i]);
        }
        Dequeue(q);
    }
    
    /*Malloc response header according to size*/
    char* result = (char*)malloc( 4*sizeof(int)+totallen);
  
    /*Set up the header value*/
    *((int*)result) = totallen+4*sizeof(int);
    *((int*)(result+8)) = temperr;
    int index = 16;
    
    Enqueue(q, head);
    
    /*Tranverse the queue again to copy data to reponse space*/
    while(q->size!=0){
        
        /*Get the front node*/
        struct dirtreenode* node= q->front->this;
        
        /*Copy the node information to space and forward the index*/
        *((int*)(result+index)) = strlen(node->name) + 1;
        index = index + sizeof(int);
        
        memcpy(result+index,node->name, strlen(node->name) + 1);
        index = index + strlen(node->name) + 1;
        
        sons = node->num_subdirs;
        *((int*)(result+index)) = sons;
        index = index + sizeof(int);
        
        /*Add the son nodes to the queue*/
        i=0;
        for(i;i<sons;i++){
            Enqueue(q,node->subdirs[i]);
        }
        Dequeue(q);
    }
    
    /*Send back the reponse*/
    send(sessfd, result,4*sizeof(int)+totallen, 0);
    freedirtree(head);
    free(q);
    return result;
}