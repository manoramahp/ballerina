/*
 *  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.ballerina.lang.statements;

import org.testng.annotations.Test;
import org.wso2.ballerina.core.exception.SemanticException;
import org.wso2.ballerina.core.utils.ParserUtils;

/**
 * Tests covering variable declarations.
 */
public class VariableDeclarationTest {

    /*
     * Negative tests.
     */
    
    @Test(expectedExceptions = {SemanticException.class },
            expectedExceptionsMessageRegExp = "duplicate-variables.bal:7: Duplicate variable 'b'.")
    public void testDuplicateVariables() {
        ParserUtils.parseBalFile("lang/statements/duplicate-variables.bal");
    }
    
    @Test(expectedExceptions = {SemanticException.class },
            expectedExceptionsMessageRegExp = "undeclared-variables.bal:2: undeclared variable 'a'")
    public void testUndeclaredVariables() {
        ParserUtils.parseBalFile("lang/statements/undeclared-variables.bal");
    }
    
    @Test(expectedExceptions = {SemanticException.class },
            expectedExceptionsMessageRegExp = "unsupported-type-variable.bal:6: type 'Foo' is undefined.")
    public void testUnsupportedTypeVariable() {
        ParserUtils.parseBalFile("lang/statements/unsupported-type-variable.bal");
    }

    @Test(expectedExceptions = SemanticException.class,
          expectedExceptionsMessageRegExp = "Duplicate constant name: b in duplicate-constant-variables.bal:4")
    public void testDuplicateConstantVariable() {
        ParserUtils.parseBalFile("lang/statements/duplicate-constant-variables.bal");
    }
    
    @Test(description = "Test defining a constant from an array type",
            expectedExceptions = {SemanticException.class},
            expectedExceptionsMessageRegExp = "array-type-constants.bal:3: constant cannot be of type 'int\\[\\]'.")
    public void testArrayTypeConstant() {
        ParserUtils.parseBalFile("lang/statements/array-type-constants.bal");
    }
}
