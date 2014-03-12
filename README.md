NetControl
==============

An Android App for rooted Android devices that lets the users see and control what domains each app accesses. This app runs as a service in the background. Everytime a pre-installed app tries to open a TCP connection, it is captured and the domain of the remote IP is identified. The domain is found using reverse DNS lookup. Then a prompt is presented to the user saying app X is trying to contact the domain Y and asks the user if they want to continue. If the user selects "Yes", the connection is allowed and the app is allowed to access the domain. If the user selects "No", the connection will be dropped. The domains that the user allowed are stored in the internal file system so that they are not prompted each time. Similarly we "remember" the denied domains for each app. This black list and white list are stored in the file system.

Details
=============
Packet interception is done by native code "nfqnl_test.c" which uses Netfilter Queue to capture each packet. The native code gives enough information to the Java code which associates a new connection with an app and identifies which app is trying to open the connection.

Obviously, this system may heavily slows the device because every single TCP connection is intercepted. The goal of this system is privacy and we have not focused much about performance. This app will be a good tool to see what domains our other apps accesses and get an awareness of our private data.


Directions to run
=================
1. Compile nfqnltest.c, rename the executable to nfqnltest 
2. Copy the executable nfqnltest over to the directory /data/data/nfqnltest/
3. Use terminal to login as root and execute the executable nfqnltest as root
4. Install the apk
5. Start the service by opening the app
6. If things went well, you should now be receiving prompts for every new domain an app tries to access

Improvements
================

The app is just a prototype and therefore usability can be vastly improved. This initial prototype can be used to see what domains our apps accesses which is one of the important privacy concerns with many popular apps. Some of the suggested improvements to the app are:
  - Right now we need to compile the native code offline and execute it before starting the app. It would be nice if we could do it as part of the initialization inside the app
  - Right now the app uses files to store the black list and white list of network domains. This can be modified to use a database
  - The packets are captured and TCP connections are intercepted for all the apps running on the device. We can improve this so that the user selects what apps they want to intercept so that these apps will be run in a "networked sandbox" mode
  - We intercept only TCP connections. If need arises, we can inspect UDP packets as well.


Note
================
I developed this app with my friend in 2012. I'm writing this ReadMe after almost 2 years. I was contacted by a random person asking more details about the project which motivated me to write a ReadMe. I may have missed some points or have made a minor mistake in the Directions to run. It was originally tested on a rooted LG p505 (LG Phoenix) device and it worked fine at the time. I don't know if this system will still run in modern Android releases. Will soon test the app again and update the ReadMe and the project.

