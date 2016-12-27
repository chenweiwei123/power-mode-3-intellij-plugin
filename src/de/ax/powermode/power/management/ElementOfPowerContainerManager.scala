/*
 * Copyright 2015 Baptiste Mesta
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ax.powermode.power.management

import java.awt._
import javax.swing._

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.{EditorFactoryAdapter, EditorFactoryEvent}
import com.intellij.openapi.editor.impl.EditorImpl
import de.ax.powermode.PowerMode
import de.ax.powermode.power.sound.PowerSound

import scala.collection.mutable
import scala.util.Try

/**
  * @author Baptiste Mesta
  */
class ElementOfPowerContainerManager extends EditorFactoryAdapter {


  val elementsOfPowerContainers = mutable.Map.empty[Editor, ElementOfPowerContainer]
  lazy val sound = new PowerSound(PowerMode.getInstance.soundsFolder, PowerMode.getInstance.valueFactor)


  val elementsOfPowerUpdateThread = new Thread(new Runnable() {
    def run {
      while (true) {
        try {
          PowerMode.getInstance.reduced
          try {
            if (PowerMode.getInstance.isEnabled &&
              PowerMode.getInstance.soundsFolder.exists(f => f.exists() && f.isDirectory)
              && PowerMode.getInstance.isSoundsPlaying) {

              sound.synchronized {
                sound.play()
              }
            } else {
              sound.synchronized {
                sound.stop()
              }

            }
            sound.setVolume(PowerMode.getInstance.valueFactor)
          } catch {
            case e: Exception =>
              e.printStackTrace()
          }
          elementsOfPowerContainers.values.foreach(_.updateElementsOfPower())
          try {
            Thread.sleep(1000 / PowerMode.getInstance.frameRate)
          }
          catch {
            case ignored: InterruptedException => {
            }
          }
        } catch {
          case e => PowerMode.logger.error(e.getMessage, e)
        }
      }
    }
  })
  elementsOfPowerUpdateThread.start()

  override def editorCreated(event: EditorFactoryEvent) {
    val editor: Editor = event.getEditor
    val isActualEditor = Try {
      editor.getColorsScheme.getClass.getName.contains("EditorImpl") && !(editor match {
        case impl: EditorImpl =>
          impl.getPreferredSize.height < 200 || impl.getPreferredSize.width < 200
        case _ =>
          false
      })
    }.getOrElse(false)
    if (isActualEditor) {
      elementsOfPowerContainers.put(editor, new ElementOfPowerContainer(editor))
    }
  }

  override def editorReleased(event: EditorFactoryEvent) {
    elementsOfPowerContainers.remove(event.getEditor)
  }

  def update(editor: Editor, pos: Point) {
    if (PowerMode.getInstance.isEnabled) {
      SwingUtilities.invokeLater(new Runnable() {
        def run {
          updateInUI(editor, pos)
        }
      })
    }
  }

  private def updateInUI(editor: Editor, pos: Point) {
    elementsOfPowerContainers.get(editor).foreach(_.update(pos))
  }


  def dispose {
    elementsOfPowerUpdateThread.interrupt()
    elementsOfPowerContainers.clear
  }
}