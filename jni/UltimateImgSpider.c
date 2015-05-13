#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <jni.h>
#include <malloc.h>
#include <android/log.h>
#include <linux/ashmem.h>
#include <asm-generic/fcntl.h>
#include <sys/mman.h>

#include "typeDef.h"


/*
 * 使用共享内存存储urlList，实现重启下载服务进程后能恢复工作现场。
 * Spider需要新申请一段内存时，向看门狗进程发出命令，并提供共享内存段名称和大小。
 * 看门狗进程创建共享内存，并将其映射到自己的内存空间，然后向Spider进程返回共享内存的文件描述符。
 * Spider进程用这个文件描述符映射此段共享内存到自己的内存空间。
 */

#define ASHM_NAME_SIZE	32

int ashmem_create_region(const char *name, u32 size)
{
	int fd, fdWithOption;
	char buf[ASHM_NAME_SIZE];

	while (name && size)
	{
		fd = open(ASHMEM_NAME_DEF, O_RDWR);
		if (fd < 0)
		{
			break;
		}

		LOGI("ashmem open success %d", fd);

		strlcpy(buf, name, sizeof(buf));
		fdWithOption = ioctl(fd, ASHMEM_SET_NAME, buf);
		if (fdWithOption < 0)
		{
			close(fd);
			break;
		}
		fdWithOption = ioctl(fd, ASHMEM_SET_SIZE, size);
		if (fdWithOption < 0)
		{
			close(fd);
			break;
		}

		break;
	}

	return fd;
}


#define ASHM_EXIST	0x12345678
#pragma pack(1)
typedef struct
{
	u32 ashmStat;
	u8 data[4];
} t_ashmBlock;
#pragma pack()

typedef struct t_ashm
{
	char name[ASHM_NAME_SIZE+1];
	int size;
	int fd;
	t_ashmBlock *ashmem;
	struct t_ashm *next;
} t_ashmNode;


t_ashmNode *ashmemChainHead=NULL;
t_ashmNode *ashmemChainTail=NULL;


t_ashmNode *findAshmemByName(const char *name)
{
	t_ashmNode *ashmNode=ashmemChainHead;

	while(ashmNode!=NULL)
	{
		if(strcmp(ashmNode->name, name)==0)
		{
			return ashmNode;
		}

		ashmNode=ashmNode->next;
	}

	return NULL;
}

int Java_com_UltimateImgSpider_WatchdogService_jniGetAshmem(JNIEnv* env,
		jobject thiz, jstring jname, jint size)
{
	int i, fd=-1;
	s64 fdWithOption;
	const char *name = (*env)->GetStringUTFChars(env, jname, NULL);

	t_ashmNode *ashmNode=findAshmemByName(name);
	if(ashmNode!=NULL)
	{
		ashmNode->ashmem->ashmStat=ASHM_EXIST;
		fd=ashmNode->fd;
	}
	else
	{
		fd = ashmem_create_region(name, size+sizeof(u32));
		if (fd >= 0)
		{
			LOGI("create ashmem name:%s size:%d fd:%d success!", name, size, fd);

			t_ashmBlock *ashm = mmap(NULL, size, PROT_READ | PROT_WRITE,
			MAP_SHARED, fd, 0);
			if (ashm != NULL)
			{
				t_ashmNode *newAshmNode=malloc(sizeof(t_ashmNode));
				if(newAshmNode!=NULL)
				{
					ashm->ashmStat=0;
					newAshmNode->ashmem=ashm;
					newAshmNode->fd=fd;
					strncpy(newAshmNode->name, name, ASHM_NAME_SIZE);
					newAshmNode->next=NULL;
					newAshmNode->size=size;

					if(ashmemChainHead==NULL)
					{
						ashmemChainHead=newAshmNode;
					}

					if(ashmemChainTail!=NULL)
					{
						ashmemChainTail->next=newAshmNode;
					}
					ashmemChainTail=newAshmNode;


					LOGI("ashmem mmap %d to watchdog process success!", (u32 )ashm);

					if(strcmp(name, "ashmTest")==0)
					{
						for(i=0; i<8; i++)
						{
							ashm->data[i]=i;
						}
					}
				}
			}
		}
	}

	(*env)->ReleaseStringUTFChars(env, jname, name);

	return fd;
}

jobject SpiderServiceInstance=NULL;

jclass SpiderServiceClass=NULL;

jmethodID getAshmemFromWatchdogMID=NULL;

void* spiderGetAshmemFromWatchdog(JNIEnv* env, const char *name, int size)
{
	void *ashmem=NULL;

	LOGI("spiderGetAshmemFromWatchdog name:%s size:%d", name, size);

	if(getAshmemFromWatchdogMID==NULL)
	{
		SpiderServiceClass = (*env)->FindClass(env,
			"com/UltimateImgSpider/SpiderService");
		if (SpiderServiceClass != NULL)
		{
			getAshmemFromWatchdogMID = (*env)->GetMethodID(env, SpiderServiceClass, "getAshmemFromWatchdog",
					"(Ljava/lang/String;I)I");
		}
	}

	if (getAshmemFromWatchdogMID != NULL)
	{
		jstring jname=(*env)->NewStringUTF(env, name);
		int fd=(*env)->CallIntMethod(env, SpiderServiceInstance, getAshmemFromWatchdogMID, jname, size);
		if(fd>=0)
		{
			ashmem=mmap(NULL, size, PROT_READ | PROT_WRITE,
						MAP_SHARED, fd, 0);
		}

	}
	return ashmem;
}




void ashmemTest(JNIEnv* env)
{
	t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, "ashmTest", 32);

	if(ashm!=NULL)
	{
		u8 i;
		for(i=0; i<8; i++)
		{
			LOGI("%d", ashm->data[i]);
		}
		if(ashm->ashmStat==ASHM_EXIST)
		{
			LOGI("ashmem already exist");
		}
	}
}






jstring Java_com_UltimateImgSpider_SpiderService_stringFromJNI(JNIEnv* env,
		jobject thiz, jstring jSrcStr)
{
	int i;
	const u8 *srcStr = (*env)->GetStringUTFChars(env, jSrcStr, NULL);
	LOGI("stringFromJNI %s", srcStr);

	SpiderServiceInstance=thiz;

	//ashmemTest(env);

	if((*env)->ExceptionOccurred(env)) {
	   return NULL;
	}

	(*env)->ReleaseStringUTFChars(env, jSrcStr, srcStr);
	return (*env)->NewStringUTF(env, "test jni !");
}


















#define MAX_SIZE_PER_URL	4096
#define SIZE_PER_URLPOOL	(1024*1024-12)

enum URL_STATE
{
	URL_PENDING, URL_DOWNLOADED
};

#define URL_TYPE_PAGE	0
#define URL_TYPE_IMG	1

#define RBT_RED     0
#define RBT_BLACK   1

#define POOL_PTR_INVALID	0xFFFFFFFF

#pragma pack(1)
typedef struct
{
	u32 poolPtr;
	u32 offset;
} urlNodeRelativeAddr;

typedef struct
{
	u64 md5_64;
	u16 state;
	u16 len;
	urlNodeRelativeAddr nextNodeAddr;

	urlNodeRelativeAddr left;
	urlNodeRelativeAddr right;
	urlNodeRelativeAddr parent;
	u32 color;
} nodePara;

typedef struct
{
	nodePara para;
	char url[MAX_SIZE_PER_URL];
} urlNode;

typedef struct
{
	urlNodeRelativeAddr head;
	urlNodeRelativeAddr tail;

	urlNodeRelativeAddr root;

	urlNodeRelativeAddr curNode;
	u32 processed;
	u32 len;
} urlTree;



typedef struct
{
	urlTree pageUrlTree;
	urlTree imgUrlTree;
	u32 urlPoolNum;
} t_spiderPara;


typedef struct memPool
{
	char mem[SIZE_PER_URLPOOL];
	u32 idleMemPtr;
	struct memPool* next;
} t_urlPool;
#pragma pack()

t_spiderPara *spiderPara=NULL;

t_urlPool *firstUrlPool = NULL;


void nodeAddrAbsToRelative(urlNode *node, urlNodeRelativeAddr *RelativeAddr)
{
	if(node!=NULL)
	{
		t_urlPool *pool=firstUrlPool;
		u32 i=0;

		while(pool!=NULL)
		{
			s64 ofs=(u32)node-(u32)pool;
			if(ofs>=0&&ofs<sizeof(t_urlPool))
			{
				RelativeAddr->poolPtr=i;
				RelativeAddr->offset=ofs;
				return;
			}
			pool=pool->next;
			i++;
		}
	}
	else
	{
		RelativeAddr->poolPtr=POOL_PTR_INVALID;
	}
}

urlNode *nodeAddrRelativeToAbs(urlNodeRelativeAddr *RelativeAddr)
{
	t_urlPool *pool=firstUrlPool;
	u32 i=0;

	if(pool!=NULL)
	{
		while(pool->next!=NULL&&i<RelativeAddr->poolPtr)
		{
			pool=pool->next;
			i++;
		}

		if(i==RelativeAddr->poolPtr&&RelativeAddr->offset<sizeof(t_urlPool))
		{
			return (urlNode *)(((u32)pool)+RelativeAddr->offset);
		}
	}

	return NULL;
}

bool isRelativeAddrEqu(urlNodeRelativeAddr *src, urlNodeRelativeAddr *dst)
{
	return src->poolPtr==dst->poolPtr && src->offset==dst->offset;
}


void urlTreeLeftRotate(urlTree *tree, urlNode *upNode)
{
	urlNodeRelativeAddr downNodeAddr=upNode->para.right;
	urlNode *downNode=nodeAddrRelativeToAbs(&downNodeAddr);

	urlNodeRelativeAddr upNodeAddr;
	nodeAddrAbsToRelative(upNode, &upNodeAddr);

	upNode->para.right=downNode->para.left;
	if(downNode->para.left.poolPtr!=POOL_PTR_INVALID)
	{
		nodeAddrRelativeToAbs(&(downNode->para.left))->para.parent=upNodeAddr;
	}

	downNode->para.parent=upNode->para.parent;

	if(upNode->para.parent.poolPtr==POOL_PTR_INVALID)
	{
		tree->root=downNodeAddr;
	}
	else if(upNode==nodeAddrRelativeToAbs(&(nodeAddrRelativeToAbs(&(upNode->para.parent))->para.left)))
	{
		nodeAddrRelativeToAbs(&(upNode->para.parent))->para.left=downNodeAddr;
	}
	else
	{
		nodeAddrRelativeToAbs(&(upNode->para.parent))->para.right=downNodeAddr;
	}

	downNode->para.left=upNodeAddr;
	upNode->para.parent=downNodeAddr;
}


void urlTreeRightRotate(urlTree *tree, urlNode *upNode)
{
	urlNodeRelativeAddr downNodeAddr=upNode->para.left;
	urlNode *downNode=nodeAddrRelativeToAbs(&downNodeAddr);

	urlNodeRelativeAddr upNodeAddr;
	nodeAddrAbsToRelative(upNode, &upNodeAddr);

	upNode->para.left=downNode->para.right;
	if(downNode->para.right.poolPtr!=POOL_PTR_INVALID)
	{
		nodeAddrRelativeToAbs(&(downNode->para.right))->para.parent=upNodeAddr;
	}

	downNode->para.parent=upNode->para.parent;

	if(upNode->para.parent.poolPtr==POOL_PTR_INVALID)
	{
		tree->root=downNodeAddr;
	}
	else if(upNode==nodeAddrRelativeToAbs(&(nodeAddrRelativeToAbs(&(upNode->para.parent))->para.left)))
	{
		nodeAddrRelativeToAbs(&(upNode->para.parent))->para.left=downNodeAddr;
	}
	else
	{
		nodeAddrRelativeToAbs(&(upNode->para.parent))->para.right=downNodeAddr;
	}

	downNode->para.left=upNodeAddr;
	upNode->para.parent=downNodeAddr;
}



t_urlPool *findUrlPoolByIndex(u32 index)
{
	t_urlPool *pool=firstUrlPool;
	u32 i=0;

	if(pool!=NULL)
	{
		while(i<index&&pool->next!=NULL)
		{
			pool=pool->next;
			i++;
		}
	}

	return (i<index)?NULL:pool;
}

urlNode *gotoNextNode(urlNode *curNode)
{
	t_urlPool *pool=findUrlPoolByIndex(curNode->para.nextNodeAddr.poolPtr);

	if(pool!=NULL)
	{
		return (urlNode*)(pool->mem+curNode->para.nextNodeAddr.offset);
	}

	return NULL;
}

#define URL_POOL_NAME_SIZE	20
char urlPoolName[URL_POOL_NAME_SIZE];
char *urlPoolIndexToName(u32 index)
{
	snprintf(urlPoolName, URL_POOL_NAME_SIZE, "urlPool_%d", index);
	LOGI("url pool name:%s", urlPoolName);
	return urlPoolName;
}

urlNode *urlNodeAllocFromPool(JNIEnv* env, u32 urlSize, urlNodeRelativeAddr *direction)
{
	urlNode *node=NULL;
	t_urlPool *urlPool = firstUrlPool;
	u32 poolIndex=0;
	u32 offset=0;
	u32 size=((urlSize+sizeof(nodePara))+3)&0xFFFFFFFC;


	if ((size > MAX_SIZE_PER_URL) || (urlPool == NULL ))
	{
		return NULL;
	}

	while (true)
	{
		if ((urlPool->idleMemPtr + size) <= SIZE_PER_URLPOOL)
		{
			offset = urlPool->idleMemPtr;
			urlPool->idleMemPtr += size;

			node=(urlNode*)(urlPool->mem + offset);
			break;
		}

		poolIndex++;
		if (urlPool->next != NULL)
		{
			urlPool = urlPool->next;
		}
		else
		{
			t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(spiderPara->urlPoolNum), sizeof(t_urlPool));
			if(ashm!=NULL)
			{
				LOGI("init new urlPool Success");

				spiderPara->urlPoolNum++;

				urlPool->next=(t_urlPool*)(ashm->data);
				urlPool = (t_urlPool*)(ashm->data);

				urlPool->idleMemPtr = 0;
				urlPool->next = NULL;

			}
			else
			{
				break;
			}
		}
	}

	if(node!=NULL)
	{
		if(direction!=NULL)
		{
			direction->poolPtr=poolIndex;
			direction->offset=offset;
		}
		
		node->para.nextNodeAddr.poolPtr=POOL_PTR_INVALID;
	}

	return node;
}


jmethodID spiderReportProcessMID=NULL;
void spiderReportProcess(JNIEnv* env)
{

	if(spiderReportProcessMID==NULL)
	{
		SpiderServiceClass = (*env)->FindClass(env,
			"com/UltimateImgSpider/SpiderService");
		if (SpiderServiceClass != NULL)
		{
			spiderReportProcessMID = (*env)->GetMethodID(env, SpiderServiceClass, "recvProcess",
					"(IIII)V");
		}
	}

	if (spiderReportProcessMID != NULL)
	{
		(*env)->CallVoidMethod(env, SpiderServiceInstance, spiderReportProcessMID, spiderPara->imgUrlTree.len, spiderPara->imgUrlTree.processed, spiderPara->pageUrlTree.len, spiderPara->pageUrlTree.processed);
	}
}

jboolean spiderParaInit(JNIEnv* env)
{

	t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, "spiderPara", sizeof(t_spiderPara));

	if(ashm!=NULL)
	{
		spiderPara=(t_spiderPara*)(ashm->data);
		if(ashm->ashmStat!=ASHM_EXIST)
		{
			spiderPara->pageUrlTree.head.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlTree.tail.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlTree.root.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlTree.curNode.poolPtr=POOL_PTR_INVALID;
			spiderPara->pageUrlTree.processed=0;
			spiderPara->pageUrlTree.len=0;

			spiderPara->imgUrlTree.head.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlTree.tail.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlTree.root.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlTree.curNode.poolPtr=POOL_PTR_INVALID;
			spiderPara->imgUrlTree.processed=0;
			spiderPara->imgUrlTree.len=0;

			spiderPara->urlPoolNum=0;
		}
		else
		{
			spiderReportProcess(env);
		}
		return true;
	}

	return false;
}

jboolean urlPoolInit(JNIEnv* env)
{
	u32 i;

	if(spiderPara->urlPoolNum==0)
	{
		t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(0), sizeof(t_urlPool));
		if(ashm!=NULL)
		{
			firstUrlPool=(t_urlPool*)(ashm->data);
			firstUrlPool->idleMemPtr = 0;
			firstUrlPool->next = NULL;
			spiderPara->urlPoolNum=1;
			return true;
		}
	}
	else
	{
		t_urlPool *urlPool;
		for(i=0; i<spiderPara->urlPoolNum; i++)
		{
			t_ashmBlock *ashm=spiderGetAshmemFromWatchdog(env, urlPoolIndexToName(i), sizeof(t_urlPool));
			if(ashm==NULL)
			{
				break;
			}
			if(ashm->ashmStat!=ASHM_EXIST)
			{
				break;
			}

			if(i==0)
			{
				firstUrlPool=(t_urlPool*)(ashm->data);
				urlPool=firstUrlPool;
			}
			else
			{
				urlPool->next=(t_urlPool*)(ashm->data);
				urlPool=(t_urlPool*)(ashm->data);
			}
		}

		if(i==spiderPara->urlPoolNum)
		{
			return true;
		}
	}

	return false;
}

jboolean Java_com_UltimateImgSpider_SpiderService_jniSpiderInit(JNIEnv* env,
		jobject thiz)
{
	SpiderServiceInstance=thiz;

	if(!spiderParaInit(env))
	{
		return false;
	}

	if(!urlPoolInit(env))
	{
		return false;
	}

	return true;
}

void rbUrlTreeFixup(urlTree *tree, urlNode *node)
{
    urlNode *parent=nodeAddrRelativeToAbs(&(node->para.parent));
	
	while(parent!=NULL)
    {
        urlNode *grandParent=nodeAddrRelativeToAbs(&(parent->para.parent));
        
		if(parent->para.color!=RBT_RED)
		{
			break;
		}
		
		
        if(parent==nodeAddrRelativeToAbs(&(grandParent->para.left)))
        {
            urlNode *uncle=nodeAddrRelativeToAbs(&(grandParent->para.right));
			
			
			while(true)
			{
				if(uncle!=NULL)
				{
					if(uncle->para.color==RBT_RED)
					{
						parent->para.color=RBT_BLACK;
						uncle->para.color=RBT_BLACK;
						grandParent->para.color=RBT_RED;
						node=grandParent;
						
						break;
					}
				}
				
				if(node==nodeAddrRelativeToAbs(&(parent->para.left)))
				{
					node=parent;
					urlTreeLeftRotate(tree, node);
				}
				break;
			}
			
			parent->para.color=RBT_BLACK;
			grandParent->para.color=RBT_RED;
			urlTreeRightRotate(tree, grandParent);
        }
        else
        {
            urlNode *uncle=nodeAddrRelativeToAbs(&(grandParent->para.left));
			
			
			while(true)
			{
				if(uncle!=NULL)
				{
					if(uncle->para.color==RBT_RED)
					{
						parent->para.color=RBT_BLACK;
						uncle->para.color=RBT_BLACK;
						grandParent->para.color=RBT_RED;
						node=grandParent;
						
						break;
					}
				}
				
				if(node==nodeAddrRelativeToAbs(&(parent->para.right)))
				{
					node=parent;
					urlTreeRightRotate(tree, node);
				}
				break;
			}
			
			parent->para.color=RBT_BLACK;
			grandParent->para.color=RBT_RED;
			urlTreeLeftRotate(tree, grandParent);
        }
        
		parent=nodeAddrRelativeToAbs(&(node->para.parent));
    }
    
    nodeAddrRelativeToAbs(&(tree->root))->para.color=RBT_BLACK;
}

void urlTreeInsert(JNIEnv* env, urlTree *tree, const u8 *newUrl, u64 newMd5_64)
{
	urlNodeRelativeAddr *nextNodeAddr=&(tree->root);
	urlNode *node=nodeAddrRelativeToAbs(nextNodeAddr);
	urlNode *parent=NULL;
	
	u16 urlLen=strlen(newUrl);

	while(node!=NULL)
	{
		if(newMd5_64>node->para.md5_64)
		{
			nextNodeAddr=&(node->para.right);
		}
		else if(newMd5_64<node->para.md5_64)
		{
			nextNodeAddr=&(node->para.left);
		}
		else
		{
			return;
		}
		
		parent=node;
		node=nodeAddrRelativeToAbs(nextNodeAddr);
	}
	
	node=urlNodeAllocFromPool(env, urlLen+1 , nextNodeAddr);
	if(node!=NULL)
	{
		strcpy(node->url, newUrl);
		node->para.md5_64 = newMd5_64;
		node->para.state = URL_PENDING;
		node->para.len=urlLen;

		node->para.left.poolPtr=POOL_PTR_INVALID;
		node->para.right.poolPtr=POOL_PTR_INVALID;

		nodeAddrAbsToRelative(parent, &(node->para.parent));

		if(parent==NULL)
		{
			node->para.color=RBT_BLACK;
		}
		else
		{
			node->para.color=RBT_RED;

			if(nodeAddrRelativeToAbs(&(parent->para.parent))!=NULL)
			{
				rbUrlTreeFixup(tree, node);
			}
		}

		tree->tail=*nextNodeAddr;
		if(tree->head.poolPtr==POOL_PTR_INVALID)
		{
			tree->head=*nextNodeAddr;
		}

		tree->len++;
		
		if(tree->len==10)
			urlTreeTraversal(nodeAddrRelativeToAbs(&(tree->root)));
	}
}

void urlTreeTraversal(urlNode *node)
{
	urlNode *left=nodeAddrRelativeToAbs(&(node->para.left));
	urlNode *right=nodeAddrRelativeToAbs(&(node->para.right));

	if(left!=NULL)
	{
		urlTreeTraversal(left);
	}

	LOGI("%08X %s", (u32)(node->para.md5_64>>32), node->url);

	if(right!=NULL)
	{
		urlTreeTraversal(right);
	}
}

//添加URL 返回列表大小
jint Java_com_UltimateImgSpider_SpiderService_jniAddUrl(JNIEnv* env,
		jobject thiz, jstring jUrl, jbyteArray jMd5, jint jType)
{
	u8 UrlAlreadyInTree=false;
	urlTree *curTree =
			(jType == URL_TYPE_PAGE) ? (&(spiderPara->pageUrlTree)) : (&(spiderPara->imgUrlTree));
	const u8 *url = (*env)->GetStringUTFChars(env, jUrl, NULL);

	//urlNode *node=nodeAddrRelativeToAbs(&(curTree->head));

	u8 *md5=(*env)->GetByteArrayElements(env, jMd5, NULL);
	u64 md5_64;
	memcpy((u8*)&md5_64, md5+4, 8);

	SpiderServiceInstance=thiz;

	LOGI("len:%d url:%s %d md5_64:%d type:%d",curTree->len, url, strlen(url), (u32)(md5_64>>32), jType);

	if(curTree->len<10)
	urlTreeInsert(env, curTree, url, md5_64);


	/*
	if(node!=NULL)
	{
		u32 i;
		for(i=0; i<curTree->len; i++)
		{
			if (node->para.md5_64 == md5_64)
			{
				if (strcmp(node->url, url) == 0)
				{
					UrlAlreadyInTree=true;
					break;
				}
			}

			node=gotoNextNode(node);
		}
	}

	if (!UrlAlreadyInTree)
	{
		u16 urlLen=strlen(url);
		urlNode *tail=nodeAddrRelativeToAbs(&(curTree->tail));
		urlNodeRelativeAddr *direction=(tail==NULL)?NULL:&(tail->para.nextNodeAddr);
		urlNode *newNode = urlNodeAllocFromPool(env, urlLen+1 , direction);
		if (newNode != NULL)
		{
			strcpy(newNode->url, url);
			newNode->para.md5_64 = md5_64;
			newNode->para.state = URL_PENDING;
			newNode->para.len=urlLen;

			if(curTree->head.poolPtr==POOL_PTR_INVALID)
			{
				nodeAddrAbsToRelative(newNode, &(curTree->head));
			}

			nodeAddrAbsToRelative(newNode, &(curTree->tail));
			curTree->len++;


		}
	}
	*/

	(*env)->ReleaseStringUTFChars(env, jUrl, url);
	(*env)->ReleaseByteArrayElements(env, jMd5, md5, 0);
	return curTree->len;
}


u16 urlSimilarity(const char *url1, u16 len1, const char *url2, u16 len2)
{
	u16 i;
	u16 len=(len1>len2)?len2:len1;
	u16 lenInWord=len/=sizeof(u32);
	const u32 *urlInWord1=(u32*)url1;
	const u32 *urlInWord2=(u32*)url2;

	for (i = 0; i<lenInWord; i++)
	{
		if (urlInWord1[i] != urlInWord2[i])
		{
			break;
		}
	}

	for(i*=sizeof(u32); i<len; i++)
	{
		if(url1[i]!=url2[i])
		{
			break;
		}
	}

	return i;
}

jstring Java_com_UltimateImgSpider_SpiderService_jniFindNextUrlToLoad(
		JNIEnv* env, jobject thiz, jstring jPrevUrl, jint jType)
{
	int i;
	urlTree *curTree =
			(jType == URL_TYPE_PAGE) ? (&(spiderPara->pageUrlTree)) : (&(spiderPara->imgUrlTree));

	char *nextUrl="";

	urlNode *curNode = NULL;
	urlNode *node = nodeAddrRelativeToAbs(&(curTree->head));
	//LOGI("jPrevUrl:%X head:%X", (u32)jPrevUrl, (u32)node);

	if(node!=NULL)
	{
		if (jPrevUrl == NULL)
		{
			for (i = 0; i < curTree->len; i++)
			{
				if (node->para.state == URL_PENDING)
				{
					curNode = node;
					break;
				}

				node=gotoNextNode(node);
			}
		}
		else
		{
			bool scanComplete = true;
			u32 urlSim = 0;
			const char *prevUrl = (*env)->GetStringUTFChars(env, jPrevUrl, NULL);
			u16 prevUrlLen=strlen(prevUrl);

			if(((u32)prevUrl)&0x03!=0)
			{
				LOGI("URL NOT ALIGN!");
			}

			curNode = nodeAddrRelativeToAbs(&(curTree->curNode));
			if(curNode!=NULL)
			{
				if(strcmp(prevUrl, curNode->url)==0)
				{
					curNode->para.state=URL_DOWNLOADED;
					curTree->processed++;
				}

				curNode=NULL;
			}

			//LOGI("prevUrl:%s curTree->len:%d", prevUrl, curTree->len);
			for (i = 0; i < curTree->len; i++)
			{
				//LOGI("url %d:%s", i, node->url);
				if (node->para.state == URL_PENDING)
				{
					if (scanComplete)
					{
						scanComplete = false;
						urlSim = urlSimilarity(prevUrl, prevUrlLen, node->url, node->para.len);
						curNode = node;
					}
					else
					{
						u32 curSim = urlSimilarity(prevUrl, prevUrlLen, node->url, node->para.len);

						if (curSim > urlSim)
						{
							urlSim = curSim;
							curNode = node;
						}
					}
				}

				node=gotoNextNode(node);
			}

			(*env)->ReleaseStringUTFChars(env, jPrevUrl, prevUrl);
		}
	}


	if(curNode!=NULL)
	{
		nextUrl=curNode->url;
		nodeAddrAbsToRelative(curNode, &(curTree->curNode));
	}

	//LOGI("nextUrl:%s", nextUrl);
	return (*env)->NewStringUTF(env, nextUrl);
}


