package com.pdc.catalina.servlets;

import com.pdc.catalina.http.MyRequest;
import com.pdc.catalina.http.MyResponse;

/**
 * author PDC
 */
public abstract class AbstractServlet {
    public abstract void doGet(MyRequest request, MyResponse response);
    public abstract void doPost(MyRequest request,MyResponse response);
}
