package com.ksj.configserver.Config;

import com.rabbitmq.client.Channel;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;

@Configuration
@Profile("dev")
class CloudConfiguration {

    private static final Logger LOG = Logger.getLogger(CloudConfiguration.class);
    @Value("${rabbitmq.queuename}")
    private String queueName;

    @Value("${rabbitmq.exchange}")
    private String queueExchange;

    @Value("${rabbitmq.routingkey}")
    private String routingkey;

    @Value("${cf.rabbit.service.name}")
    private String rabbitService;

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setVirtualHost("test");
        factory.setHost("localhost");
        // factory.setPort(15672);
        factory.setPublisherConfirms(true);// 保证消息的事务性处理rabbitmq默认的处理方式为auto
        // ack，这意味着当你从消息队列取出一个消息时，ack自动发送，mq就会将消息删除。而为了保证消息的正确处理，我们需要将消息处理修改为手动确认的方式

        Channel channel = factory.createConnection().createChannel(false);

        // 声明queue,exchange,以及绑定
        try {
            channel.exchangeDeclare(queueExchange /* exchange名称 */, "topic"/* 类型 */);
            // durable,exclusive,autodelete
            channel.queueDeclare(queueName, true, false, false, null); // (如果没有就)创建Queue
            channel.queueBind(queueName, queueExchange, routingkey);
        } catch (Exception e) {
            LOG.error("mq declare queue exchange fail ", e);
        } finally {
            try {
                channel.close();
            } catch (Exception e) {
                LOG.error("mq channel close fail", e);
            }

        }
        return factory;
    }

    // 配置接收端属性，
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // factory.setPrefetchCount(5);//这个参数设置，接收消息端，接收的最大消息数量（包括使用get、consume）,一旦到达这个数量，客户端不在接收消息。0为不限制。默认值为3.
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);// 确认模式：自动，默认
        factory.setMessageConverter(new Jackson2JsonMessageConverter());// 接收端类型转化pojo,需要序列化
        return factory;
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    // 必须是prototype类型,不然每次回调都是最后一个内容
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());// 发送端类型转化pojo,需要序列化
        return template;
    }
}
