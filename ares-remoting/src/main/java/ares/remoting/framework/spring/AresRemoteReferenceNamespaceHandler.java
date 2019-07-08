package ares.remoting.framework.spring;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * 服务引入自定义标签
 *
 * @author pdc
 */
public class AresRemoteReferenceNamespaceHandler extends NamespaceHandlerSupport {
    @Override
    public void init() {
        registerBeanDefinitionParser("reference", new RevokerFactoryBeanDefinitionParser());
    }
}
