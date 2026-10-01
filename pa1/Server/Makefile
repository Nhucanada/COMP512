all: compile-server-rmi compile-server-tcp RMIInterface.jar

RMIInterface.jar: Server/Interface/IResourceManager.java Server/TCP/TCPMessage.java Server/TCP/TCPResponse.java Server/TCP/TCPProxy.java
	@echo "Compiling interface and shared TCP communication classes"
	javac Server/Interface/IResourceManager.java Server/TCP/TCPMessage.java Server/TCP/TCPResponse.java Server/TCP/TCPProxy.java
	jar cvf RMIInterface.jar Server/Interface/IResourceManager.class Server/TCP/TCPMessage.class Server/TCP/TCPResponse.class Server/TCP/TCPProxy.class

compile-server-rmi: Server/RMI/*.java Server/Interface/IResourceManager.java Server/Common/*.java
	javac Server/RMI/*.java Server/Interface/IResourceManager.java Server/Common/*.java

compile-server-tcp: Server/TCP/*.java Server/Interface/IResourceManager.java Server/Common/*.java
	javac Server/TCP/*.java Server/Interface/IResourceManager.java Server/Common/*.java

clean:
	rm -f Server/Interface/*.class Server/Common/*.class Server/RMI/*.class Server/TCP/*.class
	rm -f RMIInterface.jar
