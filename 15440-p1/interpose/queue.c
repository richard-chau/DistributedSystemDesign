/*Queue - Linked List Implementation*/
#include<stdio.h>
#include<stdlib.h>
#include "../include/dirtree.h"

/*Definition of Node structure*/
typedef struct Node {
    struct dirtreenode* this;
    struct Node* next;
}Node;

/*Definition of Queue structure*/
typedef struct Queue{
    struct Node* front;
    struct Node* rear;
    int size;
}Queue;

/*Initialize a Queue and set up values*/
Queue* init(){
    Queue *q = malloc(sizeof(Queue));
    q->size = 0;
    q->front = NULL;
    q->rear = NULL;
    return q;
}

/*Enqueue - marshall the treenode to Node and store in queue*/
void Enqueue(Queue* queue,struct dirtreenode* x) {
    /*The treenode is not null*/
    if(x==NULL){
     fprintf(stderr,"Input is Empty\n");
        return;
    }
    /*Create Node to store the treenode*/
    Node* temp = (Node*)malloc(sizeof( Node));
    temp->this =x;
    temp->next = NULL;
    
    /*If the queue is null at this time*/
    if(queue->front == NULL && queue->rear == NULL){
        queue->front = queue->rear = temp;
        queue->size = 1;
        return;
    }
    queue->rear->next = temp;
    queue->rear = temp;
    queue->size++;
}

/*Dequeue - delete the first Node from the queue*/
void Dequeue(Queue* queue) {
    Node* temp = queue->front;
    if(queue->front == NULL) {
        fprintf(stderr,"Queue is Empty\n");
        return;
    }
    /*If only one Node is left*/
    if(queue->front == queue->rear) {
        queue->front = queue->rear = NULL;
    }
    else {
        queue->front = queue->front->next;
    }
    queue->size--;
    free(temp);
}

/*Retrieve the Front Node from the queue*/
struct dirtreenode* Front(Queue* queue) {
    /*Detect if the queue is null*/
    if(queue->front == NULL) {
    fprintf(stderr,"Queue is Empty\n");
        return NULL;
    }
    return queue->front->this;
}
