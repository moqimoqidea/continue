package com.github.continuedev.continueintellijextension.editor

import com.github.continuedev.continueintellijextension.toolWindow.JS_QUERY_POOL_SIZE
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.ui.EditorTextField
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke

/**
 * Adapted from https://github.com/cursive-ide/component-inlay-example/blob/master/src/main/kotlin/inlays/InlineEditAction.kt
 */
class InlineEditAction : AnAction(), DumbAware {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
        e.presentation.isVisible = true
    }

    private val preloadedBrowser: JBCefBrowser by lazy {
        JBCefBrowser.createBuilder().setOffScreenRendering(true).build().apply {
            jbCefClient.setProperty(
                    JBCefClient.Properties.JS_QUERY_POOL_SIZE,
                    JS_QUERY_POOL_SIZE
            )
            loadHTML("<html><body><input type='text'/></body></html>")
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(PlatformDataKeys.EDITOR) ?: return
        val project = e.getData(PlatformDataKeys.PROJECT) ?: return
        val manager = EditorComponentInlaysManager.from(editor)
        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) - 1
        val inlayRef = Ref<Disposable>()
        val panel = makePanel(makeEditor(project, inlayRef), inlayRef)
        val inlay = manager.insertAfter(lineNumber, panel)
        panel.revalidate()
        inlayRef.set(inlay)
        val viewport = (editor as? EditorImpl)?.scrollPane?.viewport
        viewport?.dispatchEvent(ComponentEvent(viewport, ComponentEvent.COMPONENT_RESIZED))
    }

    fun makePanel(editor: EditorTextField, inlayRef: Ref<Disposable>): JPanel {
        val action = object : AnAction({ "Close" }, AllIcons.Actions.Close) {
            override fun actionPerformed(e: AnActionEvent) {
                inlayRef.get().dispose()
            }
        }

        val browser = preloadedBrowser
        // Set height of the browser to be 100px
        browser.component.preferredSize = browser.component.preferredSize.apply {
            height = 100
        }

        editor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    inlayRef.get().dispose()
                }
            }
        })

        return JPanel(MigLayout("wrap 1, insets 0, gap 0!, fillx")).apply {
//            add(editor, "grow, gap 0!")
            add(browser.component, "grow, gap 0!")
            addComponentListener(object : ComponentAdapter() {
                override fun componentShown(e: ComponentEvent?) {
                    editor.requestFocus()
                }
            })
        }
    }

    fun makeEditor(project: Project, inlayRef: Ref<Disposable>): EditorTextField {
        val factory = EditorFactory.getInstance()
        val document = factory.createDocument("")

        return object : EditorTextField(document, project, FileTypes.PLAIN_TEXT) {
            //always paint pretty border
            override fun updateBorder(editor: EditorEx) = setupBorder(editor)

            override fun createEditor(): EditorEx {
                // otherwise border background is painted from multiple places
                return super.createEditor().apply {
                    //TODO: fix in editor
                    //com.intellij.openapi.editor.impl.EditorImpl.getComponent() == non-opaque JPanel
                    // which uses default panel color
                    component.isOpaque = false
                    //com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder.paintBorder
                    scrollPane.isOpaque = false
                }
            }
        }.apply {
            putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
            setOneLineMode(false)
            setPlaceholder(text)
            addSettingsProvider {
                it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
                it.colorsScheme.lineSpacing = 1f
                it.settings.isUseSoftWraps = true
            }
            selectAll()
        }
    }
}