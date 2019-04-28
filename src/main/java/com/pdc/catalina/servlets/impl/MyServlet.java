package com.pdc.catalina.servlets.impl;

import com.pdc.catalina.http.MyRequest;
import com.pdc.catalina.http.MyResponse;
import com.pdc.catalina.servlets.AbstractServlet;

/**
 * author PDC
 */
public class MyServlet extends AbstractServlet {

    public void doGet(MyRequest request, MyResponse response) {
        response.write(request.getParameter("name"));
    }

    public void doPost(MyRequest request, MyResponse response) {
        doGet(request,response);
    }
}
