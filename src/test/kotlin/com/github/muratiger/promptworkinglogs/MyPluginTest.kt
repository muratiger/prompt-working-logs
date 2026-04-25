package com.github.muratiger.promptworkinglogs

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MyPluginTest : BasePlatformTestCase() {

    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
