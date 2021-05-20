package com.restkeeper.utils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.Md5Crypt;

import java.util.Arrays;
import java.util.UUID;


public class MD5CryptUtil
{

	public static String getSalts(String password) {
		String[] salts = password.split("");
		if (salts.length < 1) {
			return "";
		}
		String mysalt = "";
		for (int i = 0; i < salts.length; i++) {
			mysalt += "$" + salts[i];
		}
		mysalt += "$";
		return mysalt;
	}
    
	public static void main(String[] args) {
		System.out.println(getSalts("abaasf"));
	}

}
