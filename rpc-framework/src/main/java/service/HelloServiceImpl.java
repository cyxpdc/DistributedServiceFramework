package service;

/**
 * @author pdc
 */
public class HelloServiceImpl implements HelloService {

    public String sayHello(String content) {
        return "hello," + content;
    }


}
