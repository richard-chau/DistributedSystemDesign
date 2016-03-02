#include <stdio.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <string.h>

int main() {
    int fd = open("abc.txt", O_CREAT | O_RDWR);
    
    char buf[20];
    size_t nbytes;
    ssize_t bytes_written;
    
    strcpy(buf, "This is a test\n");
    nbytes = strlen(buf);
    
    fprintf(stderr,"Normal fd %d\n", fd);
    fprintf(stderr,"Normal bytes %d\n", nbytes);
    fprintf(stderr,"Normal string %s\n", buf);
    

    
    bytes_written = write(fd, buf, nbytes);
    close(fd);
    return 0;
}