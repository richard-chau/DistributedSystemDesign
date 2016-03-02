PROGS=mylib.so server

all: $(PROGS)

mylib.o: mylib.c queue.o
	gcc -Wall -fPIC -DPIC -c queue.o mylib.c -g -I../include -L../lib

server.o: server.c
	gcc -Wall -fPIC -DPIC -c server.c -pthread

queue.o: queue.c queue.h
	gcc -Wall -fPIC -DPIC -c queue.c

mylib.so: mylib.o queue.o
	ld -shared -o mylib.so mylib.o queue.o -ldl

server: server.o queue.o
	gcc -o server server.o queue.o -I../include -L../lib -ldirtree -pthread

clean:
	rm -f *.o *.so $(PROGS)

