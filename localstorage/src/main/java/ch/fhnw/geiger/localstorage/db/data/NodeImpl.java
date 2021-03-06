package ch.fhnw.geiger.localstorage.db.data;

import ch.fhnw.geiger.localstorage.StorageController;
import ch.fhnw.geiger.localstorage.StorageException;
import ch.fhnw.geiger.localstorage.Visibility;
import ch.fhnw.geiger.localstorage.db.GenericController;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>The implementation of the node interface.</p>
 *
 * <p>This Class denotes one node containing sub-nodes in a tree-like structure. Each node may have
 * n children. Each node may be a skeleton-only node (contains only name and a reference to a
 * mapper), or may be materialized (contains all data). Typically when fetching a node, the node
 * is materialized but its sub-nodes are skeleton-only nodes. All skeleton nodes materialize
 * automatically if their data is accessed.</p>
 */
public class NodeImpl implements Node {

  /* an indicator whether the current object is a skeleton */
  private final SwitchableBoolean skeleton = new SwitchableBoolean(false);

  /* Contains the mapper for a skeleton to fetch any subsequent  data */
  private StorageController controller = null;

  /* contains the ordinals of a node */
  private final Map<Field, String> ordinals = new HashMap<>();

  /* contains the key/value pairs of a node */
  private final Map<String, NodeValue> values = new HashMap<>();

  /* Holds all child nodes as tuples, where the name is used as a key and
     the value is of type StorageNode */
  private final Map<String, Node> childNodes = new ConcurrentHashMap<>();

  /**
   * <p>Constructor creating a skeleton node.</p>
   *
   * @param path       the path of the node
   * @param controller the controller to fetch the full node
   */
  public NodeImpl(String path, StorageController controller) {
    skeleton.set(true);
    try {
      set(Field.PATH, path);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Oops.... this should not happen... contact developer", e);
    }
    this.controller = controller;
  }

  private NodeImpl(Node node) {
    update(node);
  }

  /**
   * <p>create a empty node for the given path.</p>
   *
   * @param path the node path
   */
  public NodeImpl(String path) {
    this(getNameFromPath(path), getParentFromPath(path));
  }

  /**
   * <p>Create a node with the given name and parent path.</p>
   *
   * @param name   name of the node
   * @param parent fully qualified parent name (path without name)
   */
  public NodeImpl(String name, String parent) {
    this(name, parent, Visibility.RED);
  }

  /**
   * <p>creates a fully fledged new empty node.</p>
   *
   * @param name   the name for the node
   * @param parent the parent of the node (may be null if root node is the parent)
   * @param vis    visibility of the node
   */
  public NodeImpl(String name, String parent, Visibility vis) {
    if (parent == null) {
      parent = "";
    }
    try {
      set(Field.PATH, parent + GenericController.PATH_DELIMITER + name);
      set(Field.VISIBILITY, vis.toString());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Oops.... this should not happen... contact developer", e);
    }
    this.skeleton.set(false);
  }

  /**
   * <p>Converts current node into a materialized node from a skeleton.</p>
   */
  private void init() {
    synchronized (skeleton) {
      if (skeleton.get()) {
        // initialize with full object
        update(controller.get(getPath()));
        skeleton.set(false);
        controller = null;
      }
    }
  }

  /**
   * <p>Returns the name part from a fully qualified node path.</p>
   *
   * @param path the fully qualified path of which the name is extracted
   * @return the name part of the path
   */
  public static String getNameFromPath(String path) {
    if (path == null) {
      return null;
    }
    return path.substring(path.lastIndexOf(GenericController.PATH_DELIMITER) + 1);
  }

  /**
   * <p>Returns the fully qualified node path of the parental node.</p>
   *
   * @param path the fully qualified path of which the parental node is extracted
   * @return the fully qualified path to the parental node
   */
  public static String getParentFromPath(String path) {
    if (path == null) {
      return null;
    }
    if (!path.contains(GenericController.PATH_DELIMITER)) {
      // assume root as parent if no delimiter found
      return "";
    }
    return path.substring(0, path.lastIndexOf(GenericController.PATH_DELIMITER));
  }

  @Override
  public NodeValue getValue(String key) {
    init();
    synchronized (values) {
      NodeValue ret = values.get(key);
      if (ret != null) {
        ret = ret.deepClone();
      }
      return ret;
    }
  }

  @Override
  public NodeValue updateValue(NodeValue value) throws StorageException {
    init();
    NodeValue ret = getValue(value.getKey());
    if (ret == null) {
      throw new StorageException("Value " + value.getKey() + " not found in node " + getName());
    }
    synchronized (values) {
      values.put(value.getKey(), value);
    }
    return ret;
  }

  @Override
  public void addValue(NodeValue value) throws StorageException {
    init();
    if (getValue(value.getKey()) != null) {
      throw new StorageException("value does already exist");
    }
    synchronized (values) {
      values.put(value.getKey(), value);
    }
  }

  @Override
  public NodeValue removeValue(String key) {
    init();
    synchronized (values) {
      return values.remove(key);
    }
  }

  @Override
  public void addChild(Node node) {
    init();
    synchronized (childNodes) {
      if (!childNodes.containsKey(node.getName())) {
        childNodes.put(node.getName(), node);
      }
    }
  }

  @Override
  public String getOwner() {
    try {
      return get(Field.OWNER);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Oops.... this should not happen... contact developer", e);
    }
  }

  @Override
  public String setOwner(String newOwner) {
    if (newOwner == null) {
      throw new NullPointerException();
    }
    try {
      return set(Field.OWNER, newOwner);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Oops.... this should not happen... contact developer", e);
    }
  }

  @Override
  public String getName() {
    try {
      return getNameFromPath(get(Field.PATH));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Oops.... this should not happen... contact developer", e);
    }
  }

  @Override
  public String getParentPath() {
    try {
      return getParentFromPath(get(Field.PATH));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Oops.... this should not happen... contact developer", e);
    }
  }

  @Override
  public String getPath() {
    try {
      return get(Field.PATH);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Oops.... this should not happen... contact developer", e);
    }
  }

  @Override
  public Visibility getVisibility() {
    try {
      Visibility ret = Visibility.valueOf(get(Field.VISIBILITY));
      if (ret == null) {
        ret = Visibility.RED;
      }
      return ret;
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Oops.... this should not happen... contact developer", e);
    }
  }

  @Override
  public Visibility setVisibility(Visibility newVisibility) {
    try {
      return Visibility.valueOf(set(Field.VISIBILITY, newVisibility.toString()));
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Oops.... this should not happen... contact developer", e);
    }
  }

  @Override
  public Map<String, NodeValue> getValues() {
    // TODO do not expose inner objects
    return values;
  }

  /**
   * <p>Gets an ordinal field of the node.</p>
   *
   * @param field the ordinal field to get
   * @return the currently set value
   * @throws ClassNotFoundException if a field does not exist
   */
  public String get(Field field) throws ClassNotFoundException {
    synchronized (skeleton) {
      if (field != Field.PATH && field != Field.NAME) {
        init();
      }
    }
    switch (field) {
      case OWNER:
      case PATH:
      case VISIBILITY:
      case LAST_MODIFIED:
        return ordinals.get(field);
      case NAME:
        return getNameFromPath(ordinals.get(Field.PATH));
      default:
        throw new ClassNotFoundException("unable to fetch field " + field);
    }
  }

  /**
   * <p>Sets an ordinal field of the node.</p>
   *
   * @param field the ordinal field to be set
   * @param value the new value
   * @return the previously set value
   * @throws ClassNotFoundException if a field does not exist
   */
  public String set(Field field, String value) throws ClassNotFoundException {
    // materialize node if required
    synchronized (skeleton) {
      if (field != Field.PATH) {
        init();
      }
    }

    // Update last modified if needed
    String current = ordinals.get(field);
    if (field != Field.LAST_MODIFIED
        && ((current != null && !current.equals(value))
        || (current == null && value != null))) {
      touch();
    }

    // return appropriate value
    switch (field) {
      case OWNER:
      case PATH:
      case VISIBILITY:
      case LAST_MODIFIED:
        return ordinals.put(field, value);
      default:
        throw new ClassNotFoundException("unable to set field " + field);
    }
  }

  public void addChildNode(NodeImpl n) {
    childNodes.put(n.getName(), n);
  }

  @Override
  public void removeChild(String name) {
    childNodes.remove(name);
  }

  @Override
  public Map<String, Node> getChildren() {
    init();

    // copy inner structure
    synchronized (childNodes) {
      Map<String, Node> ret = new HashMap<>();
      for (Map.Entry<String, Node> entry : childNodes.entrySet()) {
        ret.put(entry.getKey(), entry.getValue().deepClone());
      }

      // return copy of structure
      return ret;
    }
  }

  @Override
  public Node getChild(String name) {
    init();
    return childNodes.get(name);
  }

  @Override
  public String getChildNodesCsv() {
    init();
    if (childNodes.size() == 0) {
      return "";
    }
    return String.join(",", childNodes.keySet());
  }

  @Override
  public boolean isSkeleton() {
    return skeleton.get();
  }

  @Override
  public StorageController getController() {
    return controller;
  }

  @Override
  public StorageController setController(StorageController controller) {
    StorageController ret = this.controller;
    this.controller = controller;
    return ret;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NodeImpl)) {
      return false;
    }
    NodeImpl n2 = (NodeImpl) o;

    // check if one of the nodes is materialized
    if (!isSkeleton() || (isSkeleton() && !n2.isSkeleton())) {
      // materialize both nodes
      init();
      n2.init();

      // compare ordinals
      if (ordinals.size() != n2.ordinals.size()) {
        return false;
      }
      for (Map.Entry<Field, String> e : n2.ordinals.entrySet()) {
        try {
          if (!e.getValue().equals(get(e.getKey()))) {
            return false;
          }
        } catch (Exception ex) {
          throw new RuntimeException("Oops.... this should not happen... contact developer", ex);
        }
      }

      // compare values
      if (values.size() != n2.values.size()) {
        return false;
      }
      for (Map.Entry<String, NodeValue> e : values.entrySet()) {
        if (!e.getValue().equals(n2.getValue(e.getKey()))) {
          return false;
        }
      }

      //compare child nodes
      if (childNodes.size() != n2.childNodes.size()) {
        return false;
      }
      for (String n : childNodes.keySet()) {
        if (n2.childNodes.get(n) == null) {
          return false;
        }
      }

    } else {
      // compare just paths
      if (!getPath().equals(n2.getPath())) {
        return false;
      }

      // just compare controller
      if (controller != n2.getController()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void update(Node n2) {

    // copy basic values
    this.controller = n2.getController();
    this.skeleton.set(isSkeleton());

    // copy just the name
    this.ordinals.put(Field.NAME, ((NodeImpl) (n2)).ordinals.get(Field.NAME));

    if (!n2.isSkeleton()) {

      // copy ordinals
      synchronized (ordinals) {
        ordinals.clear();
        for (Map.Entry<Field, String> e : ((NodeImpl) n2).ordinals.entrySet()) {
          ordinals.put(e.getKey(), e.getValue());
        }
      }

      // copy values
      synchronized (values) {
        values.clear();
        for (Map.Entry<String, NodeValue> e : ((NodeImpl) n2).values.entrySet()) {
          values.put(e.getKey(), e.getValue().deepClone());
        }
      }

      // copy child nodes
      synchronized (childNodes) {
        childNodes.clear();
        for (Map.Entry<String, Node> e : n2.getChildren().entrySet()) {
          childNodes.put(e.getKey(), e.getValue().deepClone());
        }
      }
    }
    // copy last modified date (just to make sure that they are not touched
    if (((NodeImpl) (n2)).ordinals.get(Field.LAST_MODIFIED) != null) {
      this.ordinals.put(Field.LAST_MODIFIED, ((NodeImpl) (n2)).ordinals.get(Field.LAST_MODIFIED));
    }
  }

  @Override
  public NodeImpl deepClone() {
    return new NodeImpl(this);
  }

  public void touch() {
    //ordinals.put(Field.LAST_MODIFIED, "" + (new Date().getTime()));
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getPath() + "[" + getOwner() + "/" + getVisibility() + "/"
        + ordinals.get(Field.LAST_MODIFIED) + "](");
    int i = 0;
    if (values != null) {
      for (Map.Entry<String, NodeValue> e : values.entrySet()) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(e.getValue().toString());
        i++;
      }
    } else {
      sb.append("()");
    }
    sb.append(")");
    return sb.toString();
  }

}
