package ares.remoting.framework.spring;

import ares.remoting.framework.revoker.RevokerFactoryBean;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.w3c.dom.Element;

/**
 * 服务引入自定义标签解析
 * @author pdc
 */
public class RevokerFactoryBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    private static final Logger logger = LoggerFactory.getLogger(RevokerFactoryBeanDefinitionParser.class);

    @Override
    protected Class getBeanClass(Element element) {
        return RevokerFactoryBean.class;
    }

    @Override
    protected void doParse(Element element, BeanDefinitionBuilder bean) {
        try {
            String timeOut = element.getAttribute("timeout");
            String targetInterface = element.getAttribute("interface");
            String clusterStrategy = element.getAttribute("clusterStrategy");
            String remoteAppKey = element.getAttribute("remoteAppKey");
            String groupName = element.getAttribute("groupName");

            bean.addPropertyValue("timeout", Integer.parseInt(timeOut));
            bean.addPropertyValue("serviceItf", Class.forName(targetInterface));
            bean.addPropertyValue("remoteAppKey", remoteAppKey);

            if (StringUtils.isNotBlank(clusterStrategy)) {
                bean.addPropertyValue("clusterStrategy", clusterStrategy);
            }
            if (StringUtils.isNotBlank(groupName)) {
                bean.addPropertyValue("groupName", groupName);
            }
        } catch (ClassNotFoundException e) {
            logger.error("RevokerFactoryBeanDefinitionParser error.", e);
            throw new RuntimeException(e);
        }
    }
}
