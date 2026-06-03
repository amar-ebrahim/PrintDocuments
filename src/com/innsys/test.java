package com.innsys;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class test {

	public void test1(){
		
		Properties props = new Properties();
		InputStream in = null;
		try {
		    in = getClass().getClassLoader().getResourceAsStream("config.properties");
		    try {
				props.load(in);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} finally {
		    if (in != null) {
		        try { in.close(); } catch (IOException e) { }
		    }
		}
		
		
		String url = props.getProperty("service.url");
		String user = props.getProperty("service.username");
		String pass = props.getProperty("service.password");

	}
}
