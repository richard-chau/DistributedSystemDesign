/*Queue - Linked List Header Information*/

#include<stdio.h>
#include<stdlib.h>
#include "../include/dirtree.h"

/*Declaration of Node and Queue structure*/
typedef struct Node {
    struct dirtreenode* this;
    struct Node* next;
}Node;

typedef struct Queue{
    struct Node* front;
    struct Node* rear;
    int size;
}Queue;

/*Declaration of Node and Queue function*/
Queue* init();
void Enqueue(Queue* queue,struct dirtreenode* x);
void Dequeue(Queue* queue);
void Print(Queue* queue);
struct dirtreenode* Front(Queue* queue);