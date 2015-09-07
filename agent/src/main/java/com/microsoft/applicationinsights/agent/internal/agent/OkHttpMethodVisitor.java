/*
 * AppInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.agent.internal.agent;

import java.net.URL;

import com.microsoft.applicationinsights.agent.internal.coresync.impl.ImplementationsCoordinator;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Created by gupele on 7/27/2015.
 */
public final class OkHttpMethodVisitor extends AbstractHttpMethodVisitor {
    private final String fieldName;

    public OkHttpMethodVisitor(int access,
                               String desc,
                               String owner,
                               String methodName,
                               MethodVisitor methodVisitor,
                               ClassToMethodTransformationData additionalData) {
        super(access, desc, owner, methodName, methodVisitor, additionalData);

        this.fieldName = ((OkHttpClassToMethodTransformationData)additionalData).fieldName;
    }

    @Override
    public void onMethodEnter() {

        int urlLocalIndex = this.newLocal(Type.getType(URL.class));
        int stringLocalIndex = this.newLocal(Type.getType(String.class));

        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, "com/squareup/okhttp/Call", fieldName, "Lcom/squareup/okhttp/Request;");

        Label nullLabel = new Label();
        mv.visitJumpInsn(IFNULL, nullLabel);

        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, "com/squareup/okhttp/Call", fieldName, "Lcom/squareup/okhttp/Request;");

        mv.visitMethodInsn(INVOKEVIRTUAL, "com/squareup/okhttp/Request", "url", "()Ljava/net/URL;", false);
        mv.visitVarInsn(ASTORE, urlLocalIndex);

        mv.visitVarInsn(ALOAD, urlLocalIndex);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/net/URL", "toString", "()Ljava/lang/String;", false);
        mv.visitVarInsn(ASTORE, stringLocalIndex);

        super.visitFieldInsn(GETSTATIC, ImplementationsCoordinator.internalName, "INSTANCE", ImplementationsCoordinator.internalNameAsJavaName);

        mv.visitLdcInsn(getMethodName());
        mv.visitVarInsn(ALOAD, stringLocalIndex);

        mv.visitMethodInsn(INVOKEVIRTUAL, ImplementationsCoordinator.internalName, ON_ENTER_METHOD_NAME, ON_ENTER_METHOD_SIGNATURE, false);

        Label notNullLabel = new Label();
        mv.visitJumpInsn(GOTO, notNullLabel);

        mv.visitLabel(nullLabel);
        super.visitFieldInsn(GETSTATIC, ImplementationsCoordinator.internalName, "INSTANCE", ImplementationsCoordinator.internalNameAsJavaName);
        mv.visitLdcInsn(getMethodName());
        mv.visitInsn(ACONST_NULL);

        mv.visitMethodInsn(INVOKEVIRTUAL, ImplementationsCoordinator.internalName, ON_ENTER_METHOD_NAME, ON_ENTER_METHOD_SIGNATURE, false);

        mv.visitLabel(notNullLabel);
    }
}

