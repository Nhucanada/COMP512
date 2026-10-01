package Server.TCP;

import java.io.*;
import java.net.Socket;
import java.rmi.RemoteException;

/**
 * Dynamic Client RPC Proxy for TCP socket communication.
 * Marshals arbitrary interface invocations into generic request envelopes (TCPMessage)
 * and transparently unwraps server replies (TCPResponse).
 */
public class TCPProxy implements java.lang.reflect.InvocationHandler, Serializable
{
	private static final long serialVersionUID = 1L;

	private String m_host;
	private int m_port;

	public TCPProxy(String host, int port)
	{
		this.m_host = host;
		this.m_port = port;
	}

	public String getHost()
	{
		return m_host;
	}

	public int getPort()
	{
		return m_port;
	}

	/**
	 * Factory method generating a dynamic proxy instance implementing the target interface.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T create(String host, int port, Class<T> interfaceClass)
	{
		return (T) java.lang.reflect.Proxy.newProxyInstance(
			interfaceClass.getClassLoader(),
			new Class<?>[]{interfaceClass},
			new TCPProxy(host, port)
		);
	}

	@Override
	public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable
	{
		// Intercept standard Object methods locally
		String name = method.getName();
		if (name.equals("hashCode")) {
			return System.identityHashCode(proxy);
		}
		if (name.equals("equals")) {
			return proxy == args[0];
		}
		if (name.equals("toString")) {
			return "TCPProxy[" + m_host + ":" + m_port + "]";
		}

		// Encapsulate call into generic RPC message envelope
		TCPMessage message = new TCPMessage(name, method.getParameterTypes(), args);

		// Connect to remote TCP endpoint and execute synchronous request-response
		try (Socket socket = new Socket(m_host, m_port))
		{
			socket.setTcpNoDelay(true);

			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			oos.flush();
			oos.writeObject(message);
			oos.flush();

			ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
			Object responseObj = ois.readObject();

			if (responseObj instanceof TCPResponse)
			{
				TCPResponse response = (TCPResponse) responseObj;
				return response.unwrap();
			}
			else
			{
				throw new RemoteException("Unexpected RPC response object type: " + 
					(responseObj == null ? "null" : responseObj.getClass().getName()));
			}
		}
		catch (Exception e)
		{
			// If the underlying method declares this exception type, rethrow directly
			for (Class<?> declaredException : method.getExceptionTypes())
			{
				if (declaredException.isInstance(e))
				{
					throw e;
				}
			}
			// Otherwise wrap in standard RemoteException
			throw new RemoteException("TCP RPC invocation failed for '" + name + "' on " + m_host + ":" + m_port, e);
		}
	}
}
