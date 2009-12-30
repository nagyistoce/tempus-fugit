/*
 * Copyright (c) 2009, tempus-fugit committers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.code.tempusfugit.concurrency;

import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;

class RunConcurrently extends Statement {

    private final FrameworkMethod method;
    private final Statement statement;

    public RunConcurrently(FrameworkMethod method, Statement statement) {
        this.method = method;
        this.statement = statement;
    }

    public void evaluate() throws Throwable {
        if (concurrent(method)) {
            ExecutorCompletionService<Void> service = createCompletionService();
            startThreads(service);
            Throwable throwable = waitFor(service);
            if (throwable != null)
                throw throwable;
        } else
            statement.evaluate();
    }

    private ExecutorCompletionService createCompletionService() {
        return new ExecutorCompletionService(new Executor() {
            private int count;
            public void execute(Runnable runnable) {
                new Thread(runnable, method.getName() + "-Thread-" + count++).start();
            }
        });
    }

    private void startThreads(ExecutorCompletionService<Void> service) {
        for (int i = 0; i < threadCount(method); i++)
            service.submit(new StatementEvaluatingTask(statement));
    }

    private Throwable waitFor(ExecutorCompletionService<Void> service) {
        Throwable throwable = null;
        for (int i = 0; i < threadCount(method); i++) {
            try {
                service.take().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throwable = e.getCause();
                break;
            }
        }
        return throwable;
    }

    private static boolean concurrent(FrameworkMethod method) {
        return method.getAnnotation(Concurrent.class) != null;
    }

    private static int threadCount(FrameworkMethod method) {
        return method.getAnnotation(Concurrent.class).count();
    }

    private static class StatementEvaluatingTask implements Callable<Void> {
        private final Statement statement;

        public StatementEvaluatingTask(Statement statement) {
            this.statement = statement;
        }

        public Void call() throws Exception {
            try {
                statement.evaluate();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
            return null;
        }
    }
}