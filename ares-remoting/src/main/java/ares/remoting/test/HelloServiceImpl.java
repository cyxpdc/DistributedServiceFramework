package ares.remoting.test;

/**
 * @author pdc
 */
public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHello(String somebody) {
        return "hello " + somebody + "!";
    }
}
