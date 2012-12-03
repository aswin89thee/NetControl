#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <netinet/in.h>
#include <linux/types.h>
#include <linux/netfilter.h>    /* for NF_ACCEPT */
#include <errno.h>
#include <android/log.h>
#include <libnetfilter_queue/libnetfilter_queue.h>
#include <jni.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <string.h>
#include <sys/un.h>

#define PREROUTING 0
#define POSTROUTING 4
#define OUTPUT 3
#define LOCAL_SOCKET_SERVER_NAME "/test/socket/localServer"

void print(char *msg)
{
	__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", msg);
	printf(msg);
}

int connectToLocalSocket();
void sendValuesToSocket(int socket, uint32_t ip_dst, uint16_t dst_port);
int receiveResponse(int socket);
int isDomainAllowed(uint32_t ip_dst, uint16_t dst_port);

static int
cb (struct nfq_q_handle *qh, struct nfgenmsg *nfmsg,
		struct nfq_data *nfa, void *data)
{
	uint32_t ip_src, ip_dst;
	struct in_addr s_ip;
	struct in_addr d_ip;
	uint16_t src_port;
	uint16_t dst_port;
	int verdict = -1;
	int id;
	int ret, domain_allowed = 0;
	unsigned char *buffer;
	struct nfqnl_msg_packet_hdr *ph = nfq_get_msg_packet_hdr (nfa);
	if (ph)
	{
		id = ntohl (ph->packet_id);
		__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "received packet with id %d",id);
//		printf ("received packet with id %d", id);
	}
	ret = nfq_get_payload (nfa, &buffer);
	ip_src = *((uint32_t *) (buffer + 12));
	ip_dst = *((uint32_t *) (buffer + 16));
	src_port = *((uint16_t *) (buffer + 20));
	dst_port = *((uint16_t *) (buffer + 22));
	s_ip.s_addr = (uint32_t) ip_src;
	d_ip.s_addr = (uint32_t) ip_dst;
	//*(buffer + 26) = 0x00;
	// *(buffer + 27) = 0x00;

	int ipaddr1_byte0 = 0xff & ip_dst;

	if ((ipaddr1_byte0 == 192) || (ipaddr1_byte0 == 10))  {
		__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "It's a private IP %s , allowing the connection", inet_ntoa (d_ip));
		verdict = nfq_set_verdict (qh, id, NF_ACCEPT, ret, buffer);
	}
	else {
		__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "source IP %s\n", inet_ntoa (s_ip));
		__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "destination IP %s\n", inet_ntoa (d_ip));
		__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "source port %d\n", src_port);
		__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "destination port %d\n", dst_port);

		if (ret) {

			/*Calling Java function */
			domain_allowed = isDomainAllowed(ip_dst,src_port);
			__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "Return value of isDomainAllowed() is %d\n", domain_allowed);
//			domain_allowed = 0;

			if(domain_allowed == 0)
			{
				verdict = nfq_set_verdict (qh, id, NF_ACCEPT, ret, buffer);
			}
			else
			{
				verdict = nfq_set_verdict (qh, id, NF_DROP, ret, buffer);
			}
		}
	}
	if (verdict)
	__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "verdict ok\n");
	return verdict;
}

//check if a particular domain is allowed to be accessed
int isDomainAllowed(uint32_t ip_dst, uint16_t dst_port)
{
	//create a connection to local socket
	int socket = connectToLocalSocket();
	if(socket == -1) return -1;
	__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "socket successful. Socket number is %d\n",socket);

	//send the values of ip and port
	sendValuesToSocket(socket,ip_dst,dst_port);

	__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "Trying to receive server's response");
	//receive response from the local socket
	int res = receiveResponse(socket);
	__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "Response received from local server is %d\n",res);

	//close the socket
	close(socket);

	//return the received value
	return res;
}

//Create a connection to local socket
int connectToLocalSocket()
{
    __android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "Connecting to local socket\n");

    int sk, result;
    int count = 1;
    int err;

    struct sockaddr_un addr;
    socklen_t len;
    addr.sun_family = AF_LOCAL;
    /* use abstract namespace for socket path */
    addr.sun_path[0] = '\0';
    strcpy(&addr.sun_path[1], LOCAL_SOCKET_SERVER_NAME );
    len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(&addr.sun_path[1]);

    __android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "Before creating socket\n");
    sk = socket(PF_LOCAL, SOCK_STREAM, 0);
    if (sk < 0) {
        err = errno;
        __android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "%s: Cannot open socket: %s (%d)\n",__FUNCTION__, strerror(err), err);
        errno = err;
        return -1;
    }

    __android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "Before connect to Java LocalSocketServer\n");
    if (connect(sk, (struct sockaddr *) &addr, len) < 0) {
        err = errno;
        __android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "%s: connect() failed: %s (%d)\n",__FUNCTION__, strerror(err), err);
        close(sk);
        errno = err;
        return;
    }

    __android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "Connecting to Java LocalSocketServer succeed");
    return sk;
}

//send the values of ip and port to local socket
void sendValuesToSocket(int socket, uint32_t ip_dst, uint16_t dst_port)
{
	char *str = (char*)malloc(100);
	sprintf(str,"%s %u\n\0",inet_ntoa(ip_dst),dst_port); //ip<space>port\n
	__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "The string being sent to local server is: %s\n", str);
	int ret = write(socket,str,strlen(str));
	__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "Return value of write is %d",ret);
	free(str);
}

//Receive the response to the sent message from socket
int receiveResponse(int socket)
{
	char *str = (char*) malloc(100);
	int ret = recv(socket,str,100,0);
	__android_log_print(ANDROID_LOG_DEBUG, "Native:startCapture", "Response from server is %s. Return value of ret is %d", str,ret);
	int res = atoi(str);
	free(str);
	return res;
}

/*
 * native method that will be called by NetService
 */
int main(int argc, char **argv)
{
	print("native code started\n");
	struct nfq_handle *h;
	struct nfq_q_handle *qh;
	struct nfnl_handle *nh;
	int fd;
	int rv;
	char buf[4096] __attribute__ ((aligned));

	print("Inserting iptables rules");
	int ret = system("su -c \"iptables -I OUTPUT 1 -p tcp --tcp-flags SYN,RST,ACK,FIN SYN -j NFQUEUE\"");


	print("Iptables rules added successfully");

	h = nfq_open ();
	if (!h)
	{
		print("error during nfq_open()");
		return -1;
	}

	print("unbinding existing nf_queue handler for AF_INET (if any)");
	if (nfq_unbind_pf (h, AF_INET) < 0)
	{
		print("error during nfq_unbind_pf()");
		return -1;
	}

	print("binding nfnetlink_queue as nf_queue handler for AF_INET");
	if (nfq_bind_pf (h, AF_INET) < 0)
	{
		print("error during nfq_bind_pf()");
		return -1;
	}

	print("binding this socket to queue '0'");
	qh = nfq_create_queue (h, 0, &cb, NULL);
	if (!qh)
	{
		print("error during nfq_create_queue()");
		return -1;
	}

	print("setting copy_packet mode");
	if (nfq_set_mode (qh, NFQNL_COPY_PACKET, 0xffff) < 0)
	{
		print("can't set packet_copy mode");
		return -1;
	}

	fd = nfq_fd (h);

	for (;;)
	{
		if ((rv = recv (fd, buf, sizeof (buf), 0)) >= 0)
		{
			print("pkt received");
			nfq_handle_packet (h, buf, rv);
			continue;
		}
		/* if your application is too slow to digest the packets that
		 * are sent from kernel-space, the socket buffer that we use
		 * to enqueue packets may fill up returning ENOBUFS. Depending
		 * on your application, this error may be ignored. Please, see
		 * the doxygen documentation of this library on how to improve
		 * this situation.
		 */
		if (rv < 0 && errno == ENOBUFS)
		{
			print("losing packets!");
			continue;
		}
		print("recv failed");
		break;
	}

	print("unbinding from queue 0");
	nfq_destroy_queue (qh);

#ifdef INSANE
	/* normally, applications SHOULD NOT issue this command, since
	 * it detaches other programs/sockets from AF_INET, too ! */
	print("unbinding from AF_INET");
	nfq_unbind_pf (h, AF_INET);
#endif

	print("closing library handle");
	nfq_close (h);

	print("Exiting native code");
	return 0;
}

