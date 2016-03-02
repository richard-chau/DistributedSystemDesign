#include <stdlib.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <dirent.h>
 int main(int argc, char **argv)
 {
     int a;
     if(argc != 2)    
         return 1;
     char* path = "LocalFile";
     struct stat fileStat;
     if((a=stat(argv[1],&fileStat)) < 0){
         printf("the return of statt %d, errno %d \n",a,errno);
         int k = unlink(argv[1]);
         printf("the return of unlink %d, errno %d \n",k,errno);
         
         
         int m = getdirentries(-1, char *buf, 32, off_t *basep);
         printf("the return of gete %d, errno %d \n",k,errno);
         
         return 1;
         
     }
     printf("the return of statt %d, errno %d \n",a,errno);
     
     printf("Information for %s\n",argv[1]);
     printf("—————————————\n");
     printf("File Size: \t\t%d bytes\n",fileStat.st_size);
     printf("Number of Links: \t%d\n",fileStat.st_nlink);
     printf("File inode: \t\t%d\n",fileStat.st_ino);
 
     printf("File Permissions: \t");
     printf( (S_ISDIR(fileStat.st_mode)) ? "d" : "-");
     printf( (fileStat.st_mode & S_IRUSR) ? "r" : "-");
     printf( (fileStat.st_mode & S_IWUSR) ? "w" : "-");
     printf( (fileStat.st_mode & S_IXUSR) ? "x" : "-");
     printf( (fileStat.st_mode & S_IRGRP) ? "r" : "-");
     printf( (fileStat.st_mode & S_IWGRP) ? "w" : "-");
     printf( (fileStat.st_mode & S_IXGRP) ? "x" : "-");
     printf( (fileStat.st_mode & S_IROTH) ? "r" : "-");
     printf( (fileStat.st_mode & S_IWOTH) ? "w" : "-");
     printf( (fileStat.st_mode & S_IXOTH) ? "x" : "-");
     printf("\n\n");
 
     printf("The file %s a symbolic link\n", (S_ISLNK(fileStat.st_mode)) ? "is" : "is not");
     
    
     return 0;
 }
// int main()
// {
//         int file=0;
//         if((file=open("abc2.txt",O_RDONLY)) < -1)
//                 return 1;
// 
//         char buffer[19];
//         if(read(file,buffer,10) != 10)  return 1;
//         printf("%s\n",buffer);
// 
//         if(lseek(file,22,SEEK_SET) < 0) return 1;
// 
//         if(read(file,buffer,10) != 10)  return 1;
//         printf("%s\n",buffer);
// 
//        close(file);
//         return 0;
// }
// int main() {
// 	// int fd = open("test", O_RDWR);
// 	// char *buf=(char *)malloc(100);
// 	// char string[] = "DS, Let's go";
// 	// strcpy(buf, string);
// 	// int n= write(fd, buf, strlen(string));
// 	// //printf("test….%s\n",(char*)buf);
// 	// //printf("test: fd%d\n", fd);
// 	// free(buf);
// 	// close(fd);
// }


// #define BUFSIZE 128

// int main(int argc, char *argv[]){
//     ssize_t len;
//     int fd;
//     off_t base;
//     char buf[BUFSIZE];
//     struct dirent *dp;

//     fd = open("../lib", O_RDONLY);
//     while ((len = getdirentries(fd, (char *)buf, BUFSIZE, &base)) > 0) {
//         dp = (struct dirent *)buf;
//         while (len > 0) {
//             printf("%s:\n"
//             #if defined _DIRENT_HAVE_D_TYPE
//                 "\t type %llu\n",
// #endif
//                 dp->d_name,
// #if defined _DIRENT_HAVE_D_TYPE
//                 dp->d_type
// #endif
//                 );
//             len -= dp->d_reclen;
//             dp = (struct dirent *)((char *)dp + dp->d_reclen);
//         }
//     }
//     return 0;
// }
//
//void main () {
//   char *buf, *ebuf, *cp;
//   long base;
//   size_t bufsize;
//   int fd, nbytes;
//   char *path;
//   //struct stat sb;
//   struct dirent *dp;
//
//    char *tmp = "../include";
//    path = tmp;
//   if ((fd = open(path, O_RDONLY)) < 0)
//           err(2, "cannot open %s", path);
//    bufsize = 1024;
//   if ((buf = malloc(bufsize)) == NULL)
//           err(2,  "cannot malloc %lu bytes", (unsigned long)bufsize);
//   // printf("ready to get\n");
//   // printf("base start is : %ld",base);
//   while ((nbytes = getdirentries(fd, buf, bufsize, &base)) > 0) {
//           ebuf = buf + nbytes;
//           cp = buf;
//           while (cp < ebuf) {
//                   dp = (struct dirent *)cp;
//                   printf("%s\n", dp->d_name);
//                   cp += dp->d_reclen;
//           }
//   //    printf("base middle is : %ld\n",base);
//   }
//   // printf("base final is : %ld\n",base);
//   if (nbytes < 0)
//           err(2, "getdirentries");
//   free(buf);
//}