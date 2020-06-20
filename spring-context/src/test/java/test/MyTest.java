package test;

import example.scannable_scoped.CustomScopeAnnotationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author: hzc
 * @Date: 2020/05/04  20:31
 * @Description:
 */
public class MyTest {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(CustomScopeAnnotationBean.class);

	}
}
