/*
 * Copyright (C) 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.briefcase.export;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.javarosa.core.model.Constants.DATATYPE_NULL;
import static org.javarosa.core.model.DataType.CHOICE_LIST;
import static org.javarosa.core.model.DataType.GEOPOINT;
import static org.javarosa.core.model.DataType.NULL;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.javarosa.core.model.DataType;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.instance.TreeElement;

/**
 * This class represents a particular level in the model of a Form.
 * It can hold the root level model or any of its fields.
 */
class Model {

  private final FormDef form;
  private final TreeElement model;

  /**
   * Main constructor for {@link Model} that takes a {@link TreeElement} as its root.
   */
  Model(TreeElement model, FormDef form) {
    this.model = model;
    this.form = form;
  }

  /**
   * Iterates over the children of this instance and returns the flatmapped result of mapping
   * each child using the given mapper function.
   *
   * @param mapper {@link Function} that takes a model and returns a {@link List} of type T
   * @param <T>    Type parameter of the output {@link List}
   * @return a {@link List} of type T
   */
  <T> List<T> flatMap(Function<Model, List<T>> mapper) {
    return children().stream()
        .flatMap(child -> mapper.apply(child).stream())
        .collect(toList());
  }

  /**
   * Iterates over the children of this instance feeding each child to the given consumer
   * function.
   *
   * @param consumer {@link Consumer} that takes a child model
   */
  void forEach(Consumer<Model> consumer) {
    children().forEach(consumer);
  }

  /**
   * Returns the name of this {@link Model} instance, which is the
   * name of the XML tag that it represents on a Form's model.
   *
   * @return a {@link String} with the name of this {@link Model}
   */
  String getName() {
    return model.getName();
  }

  /**
   * Returns the Fully Qualified Name of this {@link Model} instance, which
   * is the concatenation of this instance's name and all its ancestors' names.
   *
   * @return a @{link String} with the FQN of this {@link Model}
   */
  String fqn() {
    return fqn(0);
  }

  /**
   * Returns the Fully Qualified Name of this {@link Model} instance, having
   * shifted a given amount of names.
   *
   * @param shift an int with the amount of names to shift from the FQN
   * @return a {@link String} with the shifted FQN of this {@link Model}
   * @see Model#fqn()
   */
  String fqn(int shift) {
    List<String> names = new ArrayList<>();
    TreeElement current = model;
    while (current.getParent() != null && current.getParent().getName() != null) {
      names.add(current.getName());
      current = (TreeElement) current.getParent();
    }
    Collections.reverse(names);
    return names
        .subList(shift, names.size())
        .stream()
        .collect(joining("-"));
  }

  /**
   * Returns the {@link DataType} of this {@link Model} instance. This will
   * be normally used when this {@link Model} instance represents a terminal
   * field of a form's model.
   *
   * @return the {@link DataType} of this {@link Model} instance}
   */
  DataType getDataType() {
    return DataType.from(model.getDataType());
  }

  /**
   * Returns the {@link List} of {@link String} names that this {@link Model} instance can be
   * associated with.
   * <p>
   * For example, {@link DataType#GEOPOINT} fields have 4 associated values.
   *
   * @return a {@link List} of {@link String} names of this {@link Model} instance
   */
  List<String> getNames() {
    return getNames(0);
  }

  /**
   * Returns the {@link List} of {@link String} names that this {@link Model} instance can be
   * associated with, shifted a given amount of names.
   *
   * @param shift an int with the amount of names to shift from the FQN
   * @return a {@link List} of shifted {@link String} names of this {@link Model} instance
   * @see Model#getNames()
   */
  List<String> getNames(int shift) {
    if (getDataType() == GEOPOINT)
      return Arrays.asList(
          fqn(shift) + "-Latitude",
          fqn(shift) + "-Longitude",
          fqn(shift) + "-Altitude",
          fqn(shift) + "-Accuracy"
      );
    if (getDataType() == CHOICE_LIST) {
      String baseName = fqn(shift);
      List<SelectChoice> choices = getChoices();
      List<String> names = new ArrayList<>(choices.size() + 1);
      names.add(baseName);
      choices.stream().map(choice -> baseName + "/" + choice.getValue()).forEachOrdered(names::add);
      return names;
    }
    if (getDataType() == NULL && model.isRepeatable())
      return singletonList("SET-OF-" + fqn(shift));
    if (getDataType() == NULL && !model.isRepeatable() && size() > 0)
      return children().stream().flatMap(e -> e.getNames(shift).stream()).collect(toList());
    return singletonList(fqn(shift));
  }

  List<SelectChoice> getChoices() {
    return Optional.ofNullable(getQuestion().getChoices()).orElse(Collections.emptyList());
  }

  private QuestionDef getQuestion() {
    return FormDef.findQuestionByRef(model.getRef(), form);
  }

  /**
   * Returns the {@link List} of repeatable group {@link Model} children of this {@link Model}
   * instance.
   *
   * @return a {@link List} of repeatable group {@link Model} children of this {@link Model} instance
   */
  List<Model> getRepeatableFields() {
    return flatten()
        .filter(field -> field.model.getDataType() == DATATYPE_NULL && field.model.isRepeatable())
        .collect(toList());
  }

  /**
   * Returns whether this {@link Model} instance represents a repeatable group or not.
   *
   * @return true if this {@link Model} instance represents a repeatable group. False otherwise.
   */
  boolean isRepeatable() {
    return model.isRepeatable();
  }

  /**
   * Returns whether this {@link Model} instance has children {@link Model} instances or not.
   *
   * @return true if this {@link Model} instance has children {@link Model} instances. False otherwise.
   */
  boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Returns the {@link Model} parent of this {@link Model} instance.
   *
   * @return the {@link Model} parent of this {@link Model} instance
   */
  Model getParent() {
    return new Model((TreeElement) model.getParent(), form);
  }

  /**
   * Returns the number of ancestors of this {@link Model} instance.
   *
   * @return an integer with the number of ancestors of this {@link Model} instance
   */
  int countAncestors() {
    int count = 0;
    Model ancestor = this;
    while (ancestor.hasParent()) {
      count++;
      ancestor = ancestor.getParent();
    }
    // We remove one to account for the root node
    return count - 1;
  }

  /**
   * Returns whether this {@link Model} is the top-most element on a form's model.
   *
   * @return true if this {@link Model} is the top-most element on a form's model. False otherwise.
   */
  boolean isRoot() {
    return countAncestors() == 0;
  }

  private boolean hasParent() {
    return model.getParent() != null;
  }

  private Stream<Model> flatten() {
    return children().stream()
        .flatMap(e -> e.size() == 0 ? Stream.of(e) : Stream.concat(Stream.of(e), e.flatten()));
  }

  private long size() {
    return model.getNumChildren();
  }

  private List<Model> children() {
    Set<String> fqns = new HashSet<>();
    List<Model> children = new ArrayList<>(model.getNumChildren());
    for (int i = 0, max = model.getNumChildren(); i < max; i++) {
      Model child = new Model(model.getChildAt(i), form);
      String fqn = child.fqn();
      if (!fqns.contains(fqn)) {
        children.add(child);
        fqns.add(fqn);
      }
    }
    return children;
  }
}
