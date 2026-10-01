package Server.TCP;

import java.io.Serializable;
import java.util.Arrays;
import java.util.UUID;

/**
 * Generic serializable RPC message envelope for TCP socket communication.
 * Encapsulates the call identifier, method name, parameter types, and arguments.
 */
public class TCPMessage implements Serializable
{
	private static final long serialVersionUID = 1L;

	private String m_id;
	private String m_methodName;
	private Class<?>[] m_paramTypes;
	private Object[] m_args;

	public TCPMessage()
	{
		this.m_id = UUID.randomUUID().toString();
	}

	public TCPMessage(String methodName, Class<?>[] paramTypes, Object[] args)
	{
		this.m_id = UUID.randomUUID().toString();
		this.m_methodName = methodName;
		this.m_paramTypes = paramTypes;
		this.m_args = args;
	}

	public TCPMessage(String id, String methodName, Class<?>[] paramTypes, Object[] args)
	{
		this.m_id = id;
		this.m_methodName = methodName;
		this.m_paramTypes = paramTypes;
		this.m_args = args;
	}

	public String getId()
	{
		return m_id;
	}

	public void setId(String id)
	{
		this.m_id = id;
	}

	public String getMethodName()
	{
		return m_methodName;
	}

	public void setMethodName(String methodName)
	{
		this.m_methodName = methodName;
	}

	public Class<?>[] getParamTypes()
	{
		return m_paramTypes;
	}

	public void setParamTypes(Class<?>[] paramTypes)
	{
		this.m_paramTypes = paramTypes;
	}

	public Object[] getArgs()
	{
		return m_args;
	}

	public void setArgs(Object[] args)
	{
		this.m_args = args;
	}

	@Override
	public String toString()
	{
		return "TCPMessage[id=" + m_id + ", method=" + m_methodName + 
			", args=" + (m_args == null ? "[]" : Arrays.toString(m_args)) + "]";
	}
}
