package com.example.trader.policy;

import com.example.trader.entity.User;
import com.example.trader.entity.Role;
import com.example.trader.repository.UserRepository;
import com.example.trader.service.UserService;
import com.example.trader.controller.loginController;
import com.example.trader.dto.SingUpDto;
import java.lang.reflect.Proxy;
import java.util.Optional;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

/** Private isolated-schema integration probe. Never exposed as an API or shipped in app.jar. */
public class Privacy653Probe {
    static void check(boolean value) {if(!value) throw new IllegalStateException("PROBE_ASSERTION_FAILED");}
    static String setting(String key) throws Exception {
        var values=new java.util.Properties();
        String location=System.getenv("SPRING_CONFIG_LOCATION");
        if(location!=null) for(String path:location.split(",")) {
            path=path.replaceFirst("^optional:","").replaceFirst("^file:","");
            var file=new java.io.File(path);
            if(file.isFile()) {
                var loader=new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
                loader.setResources(new org.springframework.core.io.FileSystemResource(file));
                values.putAll(loader.getObject());
            }
        }
        String raw=System.getenv("SPRING_APPLICATION_JSON");
        if(raw!=null) {
            var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
            var value=node.get(key);
            if(value==null) {value=node;for(String part:key.split("\\."))value=value.path(part);}
            if(value!=null && value.isTextual()) values.setProperty(key,value.asText());
        }
        String direct=System.getenv(key.toUpperCase(java.util.Locale.ROOT).replace('.','_'));
        String value=direct!=null?direct:values.getProperty(key);
        check(value!=null);
        return new org.springframework.core.env.StandardEnvironment().resolveRequiredPlaceholders(value);
    }
    public static void main(String[] args) {
        try {run();} catch(Throwable error) {
            for(Throwable cause=error;cause!=null;cause=cause.getCause()) {
                System.out.println("PROBE_ERROR_TYPE:"+cause.getClass().getName());
                if(cause instanceof java.sql.SQLException sql)System.out.println("PROBE_SQL_STATE:"+sql.getSQLState());
            }
            System.exit(1);
        }
    }
    static void run() throws Exception {
        System.out.println("PROBE_STAGE:configuration");
        String schema=System.getenv("PRIVACY653_SCHEMA");
        check(schema!=null && schema.matches("privacy653_verify_[0-9]+"));
        String url=setting("spring.datasource.url");
        url=url.replaceAll("([?&])currentSchema=[^&]*", "$1").replace("?&","?").replace("&&","&").replaceAll("[?&]$", "");
        var ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,
            setting("spring.datasource.username"),setting("spring.datasource.password"));
        var jdbc=new JdbcTemplate(ds);
        System.out.println("PROBE_STAGE:connection");
        check(schema.equals(jdbc.queryForObject("SELECT current_schema()",String.class)));
        UserRepository repo=(UserRepository)Proxy.newProxyInstance(UserRepository.class.getClassLoader(),new Class[]{UserRepository.class},(proxy,method,params)-> {
            if (method.getName().equals("findByEmail") || method.getName().equals("findByProviderAndProviderId")) return Optional.empty();
            if (method.getName().equals("save")) {
                User u=(User)params[0];Long id=jdbc.queryForObject("INSERT INTO users(email) VALUES(?) RETURNING id",Long.class,u.getEmail());
                // A deliberately invalid FK forces the receipt insert to fail after user insertion.
                Long receiptId=u.getEmail().equals("rollback@example.invalid")?id+1000000:id;
                return User.builder().id(receiptId).email(u.getEmail()).username("fixture").loginId("fixture").password("fixture").nickName("fixture").role(Role.USER).build();
            }
            throw new UnsupportedOperationException("PROBE_METHOD");
        });
        var users=new UserService(repo);
        var catalog=new PolicyCatalog(true,"653-test","a".repeat(64),"b".repeat(64));
        var target=new PolicyConsentService(jdbc,catalog,users);
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        var service=(PolicyConsentService)factory.getProxy();
        var controller=new loginController(null,null,users,service, new com.example.trader.emailverification.EmailVerificationService(new com.example.trader.emailverification.EmailVerificationProperties(false,"ap-northeast-2","","",""),null,null));
        var accepted=new PolicyAcceptance("653-test","a".repeat(64),"b".repeat(64),true,true,true);
        System.out.println("PROBE_STAGE:registration");
        var missing=new SingUpDto("fixture","not-a-real-password","fixture","fixture@example.invalid","fixture","MALE",20,null);
        try {controller.join(missing);throw new AssertionError();} catch(org.springframework.web.server.ResponseStatusException expected) {check(expected.getStatusCode().value()==400);}
        check(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)==0);
        check(controller.join(new SingUpDto("fixture","not-a-real-password","fixture","fixture@example.invalid","fixture","MALE",20,accepted)).getStatusCode().value()==201);
        Long id=jdbc.queryForObject("SELECT id FROM users",Long.class);
        var first=service.history(id);check(first.size()==1);
        service.record(id,accepted,"LOCAL");check(first.equals(service.history(id)));
        check(jdbc.queryForObject("SELECT accepted_at <= clock_timestamp() AND accepted_at > clock_timestamp()-interval '5 minutes' FROM policy_consent",Boolean.class));
        try {service.record(id,new PolicyAcceptance("old",accepted.termsSha256(),accepted.privacySha256(),true,true,true),"LOCAL");throw new AssertionError();} catch(org.springframework.web.server.ResponseStatusException expected){check(expected.getStatusCode().value()==409);}
        System.out.println("PROBE_STAGE:rollback");
        try {controller.join(new SingUpDto("rollback","fixture-password","fixture","rollback@example.invalid","fixture","MALE",20,accepted));throw new AssertionError();} catch(org.springframework.dao.DataAccessException expected) {}
        check(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)==1);
        System.out.println("PROBE_STAGE:oauth");
        User oauth=service.registerOAuth("google","synthetic-provider-id","oauth@example.invalid","fixture",accepted);
        check(service.history(oauth.getId()).get(0).get("channel").equals("OAUTH_GOOGLE"));
        check(service.history(-1L).isEmpty());
        jdbc.update("DELETE FROM users");check(jdbc.queryForObject("SELECT count(*) FROM policy_consent",Integer.class)==0);
        System.out.println("PRIVACY653_PROBE_OK");
    }
}
