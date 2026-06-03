package com.innsys;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigProps {

	private InputStream inputStream;
	private String fnUser;
	private String fnPwd;
	private String fnOS;
	private String fnStanza;
	private String fnURI;
	private String fnPrintFolderId;
	private String fnDocClass;
	
	
	
	public ConfigProps() {
		getPropValues();
	}
	
	
	public String getFnUser(){
		return this.fnUser;
	}
	
	public String getFnPwd(){
		return this.fnPwd;
	}
	
	public String getFnOS(){
		return this.fnOS;
	}
	
	public String getFnStanza(){
		return this.fnStanza;
	}
	
	public String getFnURI(){
		return this.fnURI;
	}
	

	public String getFnPrintFolderId(){
		return this.fnPrintFolderId;
	}
	
	public String getDocClass(){
		return this.fnDocClass;
	}
	
	
	private void getPropValues() {
 
		try {
			
			Properties prop = new Properties();
			String propFileName = "config.properties";
 
			inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
 
			if (inputStream != null) {
				prop.load(inputStream);
			} else {
				throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
			}
 
			this.fnUser = prop.getProperty("FnUser");
			this.fnPwd = prop.getProperty("FnPwd");
			this.fnURI = prop.getProperty("FnURI");
			this.fnOS = prop.getProperty("FnOS");
			this.fnStanza = prop.getProperty("FnStanza");
			this.fnPrintFolderId = prop.getProperty("FnPrintFolderId");
			this.fnDocClass = prop.getProperty("FnDocClass");
			
			
		} catch (Exception e) {
			System.out.println("Exception: " + e);
		} finally {
			try {
				inputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		

	}
	
}