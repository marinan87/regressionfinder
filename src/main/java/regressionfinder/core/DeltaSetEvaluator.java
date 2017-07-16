package regressionfinder.core;

import static java.util.stream.Collectors.toList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.deltadebugging.ddcore.DeltaSet;
import org.deltadebugging.ddcore.tester.JUnitTester;
import org.hamcrest.SelfDescribing;
import org.junit.runner.manipulation.NoTestsRemainException;

import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import regressionfinder.isolatedrunner.DeltaDebuggerTestRunner;
import regressionfinder.isolatedrunner.IsolatedClassLoaderAwareJUnitTestRunner;
import regressionfinder.isolatedrunner.IsolatedURLClassLoader;
import regressionfinder.isolatedrunner.JUnitTestRunner;
import regressionfinder.isolatedrunner.MethodDescriptor;

public class DeltaSetEvaluator extends JUnitTester {
	
	private final URL[] urls;
	private final Throwable throwable;
	private final EvaluationContext evaluationContext;
	
	public DeltaSetEvaluator(EvaluationContext evaluationContext) throws Exception {
		super();
		
		this.evaluationContext = evaluationContext;
		urls = collectClasspaths(); 
		throwable = obtainOriginalStacktrace();
	}
	
	private URL[] collectClasspaths() throws Exception {		
		List<URL> urlList = new ArrayList<>();
		// These paths are required because DeltaDebuggerTestRunner needs to find JUnit test classes inside StagingArea subfolder.
		// See implementation of DeltaDebuggerTestRunner.runTest().
		urlList.add(new URL("file:/" + evaluationContext.getWorkingAreaClassesPath() + "/"));
		urlList.add(new URL("file:/" + evaluationContext.getWorkingAreaTestClassesPath() + "/"));
		
		// This is required because IsolatedURLClassLoader should be able to locate DeltaDebuggerTestRunner and JUnitTestRunner class, 
		// which reside in the plugin project.
		urlList.add(new URL("file:" + DeltaDebuggerTestRunner.class.getProtectionDomain().getCodeSource().getLocation().getPath()));
		urlList.add(JUnitTester.class.getProtectionDomain().getCodeSource().getLocation());
		urlList.add(NoTestsRemainException.class.getProtectionDomain().getCodeSource().getLocation());
		urlList.add(SelfDescribing.class.getProtectionDomain().getCodeSource().getLocation());

		return (URL[]) urlList.toArray(new URL[0]);
	}
	
	private Throwable obtainOriginalStacktrace() {
		SourceCodeManipulator.copyToStagingAreaWithModifications(evaluationContext.getWorkingArea(), evaluationContext.getFaultyVersion(), new ArrayList<>());
		
		return (Throwable) runMethodInIsolatedTestRunner(JUnitTestRunner.class, 
				new MethodDescriptor("getOriginalException", null, null));
	}
	
	@Override
	public int test(DeltaSet c) {
		@SuppressWarnings("unchecked")
		List<SourceCodeChange> selectedChangeSet = (List<SourceCodeChange>) c.stream().collect(toList());
		return testSelectedChangeSet(selectedChangeSet); 
	}
	
	private int testSelectedChangeSet(List<SourceCodeChange> selectedSourceCodeChangeSet) {
		// TODO: evaluationcontext, sourcecodemanipulator, deltasetevaluator - singleton beans
		SourceCodeManipulator.copyToStagingAreaWithModifications(evaluationContext.getWorkingArea(), evaluationContext.getReferenceVersion(), selectedSourceCodeChangeSet);
		
		return (int) runMethodInIsolatedTestRunner(DeltaDebuggerTestRunner.class, 
				new MethodDescriptor("runTest", new Class<?>[] { Throwable.class }, new Object[] { throwable }));
	}
	
	private <T extends IsolatedClassLoaderAwareJUnitTestRunner> Object runMethodInIsolatedTestRunner(Class<T> clazz, MethodDescriptor methodDescriptor) {
		try (IsolatedURLClassLoader isolatedClassLoader = new IsolatedURLClassLoader(urls)) {
			Class<?> runnerClass = isolatedClassLoader.loadClass(clazz.getName());
			Constructor<?> constructor = runnerClass.getConstructor(String.class, String.class);
			
			Object isolatedTestRunner = constructor.newInstance(evaluationContext.getTestClassName(), evaluationContext.getTestMethodName());
		
			Method method = isolatedTestRunner.getClass().getMethod(methodDescriptor.getMethodName(), methodDescriptor.getParameterTypes());
			return method.invoke(isolatedTestRunner, methodDescriptor.getArgs());			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
