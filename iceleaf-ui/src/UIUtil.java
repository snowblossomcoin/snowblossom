package snowblossom.iceleaf;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class UIUtil
{
  public static void applyLook(Component comp, IceLeaf ice_leaf)
  {

    if (comp instanceof Container)
    {
      Container ct = (Container) comp;
      for(Component c : ct.getComponents())
      {
        applyLook(c, ice_leaf);
      }
    }

    if (comp instanceof JTextArea)
    {
      JTextArea e = (JTextArea)comp;
      if (!e.isEditable())
      {
        comp.setBackground(ice_leaf.getTextAreaBGColor());
      }
      comp.setFont(ice_leaf.getFixedFont().deriveFont(0,12));
    }
    else if (comp instanceof JTextField)
    {
      comp.setFont(ice_leaf.getFixedFont().deriveFont(0,12));
    }
    else if (comp instanceof JLabel)
    {
      //int size = comp.getFont().getSize();
      //comp.setFont(ice_leaf.getVariableFont().deriveFont(0,size));

    }
    else if (comp instanceof JTabbedPane)
    {
      //int size = comp.getFont().getSize();
      //comp.setFont(ice_leaf.getVariableFont().deriveFont(0,size));

    }
    

  }

}
