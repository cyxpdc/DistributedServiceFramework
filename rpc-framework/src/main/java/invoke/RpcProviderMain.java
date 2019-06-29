package invoke;

import framework.ProviderReflect;
import service.HelloService;
import service.HelloServiceImpl;

/**
 * 服务的启动
 *
 * @author pdc
 */
public class RpcProviderMain {

    public static void main(String[] args) throws Exception {
        HelloService service = new HelloServiceImpl();
        ProviderReflect.provider(service, 8083);
    }

}
