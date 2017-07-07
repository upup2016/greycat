/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package greycat.generator;

import greycat.Graph;
import greycat.Type;
import greycat.language.*;
import greycat.language.Class;
import greycat.utility.HashHelper;
import greycat.utility.MetaConst;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.Visibility;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;

class ClassTypeGenerator {

    static JavaSource[] generate(String packageName, Model model) {
        JavaSource[] sources = new JavaSource[model.classes().length];

        for (int i = 0; i < model.classes().length; i++) {
            sources[i] = generateClass(packageName, model.classes()[i]);
        }

        return sources;
    }

    private static JavaClassSource generateClass(String packageName, Class classType) {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
        javaClass.setPackage(packageName);
        javaClass.setName(classType.name());
        javaClass.addImport(Type.class);

        if (classType.parent() != null) {
            javaClass.setSuperType(packageName + "." + classType.parent().name());
        } else {
            javaClass.setSuperType("greycat.base.BaseNode");
        }

        StringBuilder TS_GET_SET = new StringBuilder();
        classType.properties().forEach(o -> {
            if (o instanceof Attribute) {
                Attribute attribute = (Attribute) o;
                if (TypeManager.isPrimitive(attribute.type())) {
                    TS_GET_SET.append("get " + attribute.name() + "() : " + TypeManager.classTsName(attribute.type()) + " {return this.get" + Generator.upperCaseFirstChar(attribute.name()) + "();}\n");
                    TS_GET_SET.append("set " + attribute.name() + "(p : " + TypeManager.classTsName(attribute.type()) + "){ this.set" + Generator.upperCaseFirstChar(attribute.name()) + "(p);}\n");
                }
            }
        });
        //generate TS getter and setter
        javaClass.getJavaDoc().setFullText("<pre>{@extend ts\n" + TS_GET_SET + "\n}\n</pre>");

        // init method
        MethodSource<JavaClassSource> init = javaClass.addMethod()
                .setName("init")
                .setVisibility(Visibility.PUBLIC)
                .setReturnTypeVoid();
        StringBuilder initBodyBuilder = new StringBuilder();

        // override typeAt
        MethodSource<JavaClassSource> typeAt = javaClass.addMethod()
                .setName("typeAt")
                .setVisibility(Visibility.PUBLIC)
                .setReturnType(int.class);
        typeAt.addParameter("int", "index");
        StringBuilder typeAtBodyBuilder = new StringBuilder();

        // override getAt
        MethodSource<JavaClassSource> getAt = javaClass.addMethod()
                .setName("getAt")
                .setVisibility(Visibility.PUBLIC)
                .setReturnType(Object.class);
        getAt.addParameter("int", "index");
        StringBuilder getAtBodyBuilder = new StringBuilder();

        // override setAt
        MethodSource<JavaClassSource> setAt = javaClass.addMethod()
                .setName("setAt")
                .setVisibility(Visibility.PUBLIC)
                .setReturnType(greycat.Node.class);
        setAt.addParameter("int", "index");
        setAt.addParameter("int", "type");
        setAt.addParameter("Object", "value");
        StringBuilder setAtBodyBuilder = new StringBuilder();

        // create method
        MethodSource<JavaClassSource> create = javaClass.addMethod()
                .setName("create")
                .setVisibility(Visibility.PUBLIC)
                .setStatic(true);
        create.addParameter("long", "p_world");
        create.addParameter("long", "p_time");
        create.addParameter(Graph.class, "p_graph");
        create.setReturnType(classType.name());
        create.setBody("return (" + javaClass.getName() + ") p_graph.newTypedNode(p_world, p_time, " + javaClass.getName() + ".META.name);");


        // constructor
        MethodSource<JavaClassSource> constructor = javaClass.addMethod().setConstructor(true);
        constructor.addParameter("long", "p_world");
        constructor.addParameter("long", "p_time");
        constructor.addParameter("long", "p_id");
        constructor.addParameter(Graph.class, "p_graph");
        constructor.setBody("super(p_world, p_time, p_id, p_graph);");
        constructor.setVisibility(Visibility.PUBLIC);


        javaClass.addField()
                .setVisibility(Visibility.PUBLIC)
                .setFinal(true)
                .setName("META")
                .setType(greycat.utility.Meta.class)
                .setLiteralInitializer("new greycat.utility.Meta(" + "\"" + classType.name() + "\"" + ","
                        + HashHelper.hash(classType.name()) + ","
                        + HashHelper.hash(classType.name()) + ");")
                .setStatic(true);

        // attributes
        classType.properties().forEach(o -> {
            // constants
            if (o instanceof Constant) {
                Constant constant = (Constant) o;
                String value = constant.value();

                if (constant.type().equals("Task")) {
                    typeAtBodyBuilder.append("if (index == ").append(constant.name().toUpperCase()).append(".hash) {").append("return Type.TASK;").append("}");
                    getAtBodyBuilder.append("if (index == ").append(constant.name().toUpperCase()).append(".hash) {").append("return " + constant.name().toUpperCase() + ".value" + ";").append("}");
                    setAtBodyBuilder.append("if (type == Type.TASK && index == ").append(constant.name().toUpperCase() + ".hash)").append("{")
                            .append(constant.name().toUpperCase() + ".value ").append("= (greycat.Task) value;").append("return this;").append("}");

                    if (value != null) {
                        value = "greycat.Tasks.newTask().parse(\"" + value.replaceAll("\"", "'").trim() + "\",null)";
                    }
                } else if (!constant.type().equals("String") && value != null) {
                    value = value.replaceAll("\"", "");
                }

                javaClass.addField()
                        .setVisibility(Visibility.PUBLIC)
                        .setFinal(true)
                        .setName(constant.name().toUpperCase())
                        .setType(MetaConst.class)
                        .setLiteralInitializer("new greycat.utility.MetaConst(\"" + constant.name() + "\", "
                                + TypeManager.typeName(constant.type()) + ", "
                                + HashHelper.hash(constant.name()) + ", " + value + ");")
                        .setStatic(true);


            } else if (o instanceof Attribute) {
                Attribute att = (Attribute) o;

                javaClass.addField()
                        .setVisibility(Visibility.PUBLIC)
                        .setFinal(true)
                        .setName(att.name().toUpperCase())
                        .setType(greycat.utility.Meta.class)
                        .setLiteralInitializer("new greycat.utility.Meta(\"" + att.name() + "\", "
                                + TypeManager.typeName(att.type()) + ", "
                                + HashHelper.hash(att.name()) + ");")
                        .setStatic(true);


                // getter
                MethodSource<JavaClassSource> getter = javaClass.addMethod();
                getter.setVisibility(Visibility.PUBLIC).setFinal(true);
                getter.setReturnType(TypeManager.className(att.type()));
                getter.setName("get" + Generator.upperCaseFirstChar(att.name()));

                if (TypeManager.isPrimitive(att.type())) {
                    getter.setBody("return (" + TypeManager.className(att.type()) + ") super.get(" + att.name().toUpperCase() + ".name);");
                } else {
                    getter.setBody("if(super.get(" + att.name().toUpperCase() + ".name) == null) {return null;} return (" + TypeManager.className(att.type()) + ") super.get(" + att.name().toUpperCase() + ".name);");

                    MethodSource<JavaClassSource> getOrCreate = javaClass.addMethod();
                    getOrCreate.setVisibility(Visibility.PUBLIC).setFinal(true);
                    getOrCreate.setReturnType(TypeManager.className(att.type()));
                    getOrCreate.setName("getOrCreate" + Generator.upperCaseFirstChar(att.name()));
                    getOrCreate.setBody("return (" + TypeManager.className(att.type()) + ") super.getOrCreate(" + att.name().toUpperCase() + ".name, " + att.name().toUpperCase() + ".type);");
                }

                // setter
                if (TypeManager.isPrimitive(att.type())) {
                    javaClass.addMethod()
                            .setVisibility(Visibility.PUBLIC).setFinal(true)
                            .setName("set" + Generator.upperCaseFirstChar(att.name()))
                            .setReturnType(classType.name())
                            .setBody("super.set(" + att.name().toUpperCase() + ".name, " + att.name().toUpperCase()
                                    + ".type,value);\nreturn this;"
                            )
                            .addParameter(TypeManager.className(att.type()), "value");
                }

                // init
                if (att.value() != null) {
                    initBodyBuilder.append(DefaultValueGenerator.createMethodBody(att).toString());
                }

            } else if (o instanceof Relation) {
                Relation rel = (Relation) o;
                // field
                javaClass.addField()
                        .setVisibility(Visibility.PUBLIC)
                        .setFinal(true)
                        .setName(rel.name().toUpperCase())
                        .setType(greycat.utility.Meta.class)
                        .setLiteralInitializer("new greycat.utility.Meta(\"" + rel.name() + "\", "
                                + Type.RELATION + ", "
                                + HashHelper.hash(rel.name()) + ");")
                        .setStatic(true);

                // getter
                String resultType = rel.type();
                MethodSource<JavaClassSource> getter = javaClass.addMethod();
                getter.setVisibility(Visibility.PUBLIC);
                getter.setFinal(true);
                getter.setReturnTypeVoid();
                getter.setName("get" + Generator.upperCaseFirstChar(rel.name()));
                getter.addParameter("greycat.Callback<" + resultType + "[]>", "callback");
                getter.setBody(
                        "this.traverse(" + rel.name().toUpperCase() + ".name ,new greycat.Callback<greycat.Node[]>() {\n" +
                                "@Override\n" +
                                "public void on(greycat.Node[] nodes) {\n" +
                                "if(nodes != null) {\n" +
                                resultType + "[] result = new " + resultType + "[nodes.length];\n" +
                                "for(int i=0;i<result.length;i++) {\n" +
                                "result[i] = (" + resultType + ") nodes[i];\n" +
                                "}\n" +
                                "callback.on(result);\n" +
                                "} else {\n" +
                                "callback.on(new " + resultType + "[0]);\n" +
                                "}\n" +
                                "}\n" +
                                "});"
                );

                // addTo
                StringBuilder addToBodyBuilder = new StringBuilder();
                MethodSource<JavaClassSource> add = javaClass.addMethod();
                add.setVisibility(Visibility.PUBLIC).setFinal(true);
                add.setName("addTo" + Generator.upperCaseFirstChar(rel.name()));
                add.setReturnType(classType.name());
                add.addParameter(rel.type(), "value");
                addToBodyBuilder.append("super.addToRelation(").append(rel.name().toUpperCase()).append(".name").append(", value);");
                if (rel.opposite() != null) {
                    addToBodyBuilder.append(createAddOppositeBody(rel.type(), rel).toString());
                }
                addToBodyBuilder.append("return this;");
                add.setBody(addToBodyBuilder.toString());

                // remove
                StringBuilder removeFromBodyBuilder = new StringBuilder();
                MethodSource<JavaClassSource> remove = javaClass.addMethod();
                remove.setVisibility(Visibility.PUBLIC).setFinal(true);
                remove.setName("removeFrom" + Generator.upperCaseFirstChar(rel.name()));
                remove.setReturnType(classType.name());
                remove.addParameter(rel.type(), "value");
                removeFromBodyBuilder.append(classType.name()).append(" self = this;");
                removeFromBodyBuilder.append("super.removeFromRelation(").append(rel.name().toUpperCase()).append(".name").append(", value);");
                if (rel.opposite() != null) {
                    removeFromBodyBuilder.append(createRemoveOppositeBody(rel.type(), rel).toString());
                }
                removeFromBodyBuilder.append("return this;");
                remove.setBody(removeFromBodyBuilder.toString());

            } else if (o instanceof Reference) {
                Reference ref = (Reference) o;
                // field
                javaClass.addField()
                        .setVisibility(Visibility.PUBLIC)
                        .setFinal(true)
                        .setName(ref.name().toUpperCase())
                        .setType(greycat.utility.Meta.class)
                        .setLiteralInitializer("new greycat.utility.Meta(\"" + ref.name() + "\", "
                                + Type.RELATION + ", "
                                + HashHelper.hash(ref.name()) + ");")
                        .setStatic(true);

                // getter
                String resultType = ref.type();
                MethodSource<JavaClassSource> getter = javaClass.addMethod();
                getter.setVisibility(Visibility.PUBLIC);
                getter.setFinal(true);
                getter.setReturnTypeVoid();
                getter.setName("get" + Generator.upperCaseFirstChar(ref.name()));
                getter.addParameter("greycat.Callback<" + resultType + ">", "callback");
                getter.setBody(
                        "this.traverse(" + ref.name().toUpperCase() + ".name,new greycat.Callback<greycat.Node[]>() {\n" +
                                "@Override\n" +
                                "public void on(greycat.Node[] nodes) {\n" +
                                "if(nodes != null) {\n" +
                                resultType + " result = (" + resultType + ") nodes[0];\n" +
                                "callback.on(result);\n" +
                                "} else {\n" +
                                "callback.on(null);\n" +
                                "}\n" +
                                "}\n" +
                                "});"
                );
                // setter
                StringBuilder addToBodyBuilder = new StringBuilder();
                MethodSource<JavaClassSource> add = javaClass.addMethod();
                add.setVisibility(Visibility.PUBLIC).setFinal(true);
                add.setName("set" + Generator.upperCaseFirstChar(ref.name()));
                add.setReturnType(classType.name());
                add.addParameter(ref.type(), "value");
                addToBodyBuilder.append("if(value != null) {");
                addToBodyBuilder.append("super.removeFromRelation(").append(ref.name().toUpperCase()).append(".name, value );");
                addToBodyBuilder.append("super.addToRelation(").append(ref.name().toUpperCase()).append(".name, value );");
                if (ref.opposite() != null) {
                    addToBodyBuilder.append(createAddOppositeBody(ref.type(), ref).toString());
                }
                addToBodyBuilder.append("}");
                addToBodyBuilder.append("return this;");
                add.setBody(addToBodyBuilder.toString());

                // remove
                StringBuilder removeFromBodyBuilder = new StringBuilder();
                MethodSource<JavaClassSource> remove = javaClass.addMethod();
                remove.setVisibility(Visibility.PUBLIC).setFinal(true);
                String refName = Generator.upperCaseFirstChar(ref.name());
                String refType = Generator.upperCaseFirstChar(ref.type());
                remove.setName("remove" + refName);
                remove.setReturnTypeVoid();
                remove.addParameter("greycat.Callback<Boolean>", "callback");
                removeFromBodyBuilder.append(classType.name() + " self = this;");
                removeFromBodyBuilder.append("get" + refName + "(new greycat.Callback<" + refType + ">() {");
                removeFromBodyBuilder.append("@Override\n");
                removeFromBodyBuilder.append("public void on(" + refType + " value) {");
                removeFromBodyBuilder.append("self.removeFromRelation(").append(ref.name().toUpperCase()).append(".name, value);");
                if (ref.opposite() != null) {
                    removeFromBodyBuilder.append(createRemoveOppositeBody(ref.type(), ref).toString());
                }
                removeFromBodyBuilder.append("callback.on(true);");
                removeFromBodyBuilder.append("}");
                removeFromBodyBuilder.append("});");
                remove.setBody(removeFromBodyBuilder.toString());

            } else if (o instanceof Index) {
                greycat.language.Index li = (greycat.language.Index) o;

                // field for meta
                javaClass.addField()
                        .setVisibility(Visibility.PUBLIC)
                        .setFinal(true)
                        .setName(li.name().toUpperCase())
                        .setType(greycat.utility.Meta.class)
                        .setLiteralInitializer("new greycat.utility.Meta(\"" + li.name() + "\", "
                                + Type.INDEX + ", "
                                + HashHelper.hash(li.name()) + ");")
                        .setStatic(true);

                StringBuilder indexedAttBuilder = new StringBuilder();
                for (AttributeRef attRef : li.attributes()) {
                    indexedAttBuilder.append(li.type() + "." + attRef.ref().name().toUpperCase() + ".name");
                    indexedAttBuilder.append(",");
                }
                indexedAttBuilder.deleteCharAt(indexedAttBuilder.length() - 1);
                // index method
                MethodSource<JavaClassSource> indexMethod = javaClass.addMethod()
                        .setName("index" + Generator.upperCaseFirstChar(li.name()))
                        .setVisibility(Visibility.PUBLIC)
                        .setFinal(true)
                        .setReturnType("greycat.Index");
                indexMethod.addParameter(Generator.upperCaseFirstChar(li.type()), "value");

                StringBuilder indexBodyBuilder = new StringBuilder();
                indexBodyBuilder.append("greycat.Index index = this.getIndex(META.name);");
                indexBodyBuilder.append("if (index == null) {");
                indexBodyBuilder.append("index = (greycat.Index) this.getOrCreate(META.name, Type.INDEX);");
                indexBodyBuilder.append("index.declareAttributes(null, " + indexedAttBuilder.toString() + ");");
                indexBodyBuilder.append("}");
                indexBodyBuilder.append("index.update(value);");

                if (li.opposite() != null) {
                    indexBodyBuilder.append(createAddOppositeBody(li.type(), li).toString());
                }
                indexBodyBuilder.append("return index;");

                indexMethod.setBody(indexBodyBuilder.toString());

                // unindex method
                MethodSource<JavaClassSource> unindexMethod = javaClass.addMethod()
                        .setName("unindex" + Generator.upperCaseFirstChar(li.name()))
                        .setVisibility(Visibility.PUBLIC)
                        .setFinal(true)
                        .setReturnTypeVoid();
                unindexMethod.addParameter(Generator.upperCaseFirstChar(li.type()), "value");

                StringBuilder unindexBodyBuilder = new StringBuilder();
                unindexBodyBuilder.append(classType.name()).append(" self = this;");
                unindexBodyBuilder.append("greycat.Index index = this.getIndex(META.name);");
                unindexBodyBuilder.append("if (index != null) {");
                unindexBodyBuilder.append("index.unindex(value);");
                if (li.opposite() != null) {
                    unindexBodyBuilder.append(createRemoveOppositeBody(li.type(), li).toString());
                }
                unindexBodyBuilder.append("}");

                unindexMethod.setBody(unindexBodyBuilder.toString());

                // find method
                MethodSource<JavaClassSource> find = javaClass.addMethod();
                find.setVisibility(Visibility.PUBLIC).setFinal(true);
                find.setName("find" + Generator.upperCaseFirstChar(li.name()));
                find.setReturnTypeVoid();
                for (AttributeRef indexedAtt : li.attributes()) {
                    find.addParameter("String", indexedAtt.ref().name());
                }
                find.addParameter("greycat.Callback<" + li.type() + "[]>", "callback");
                StringBuilder paramsBuilder = new StringBuilder();
                for (AttributeRef indexedAtt : li.attributes()) {
                    paramsBuilder.append(indexedAtt.ref().name() + ",");
                }
                paramsBuilder.deleteCharAt(paramsBuilder.length() - 1);
                StringBuilder findBodyBuilder = createFindMethodBody(li, paramsBuilder);
                find.setBody(findBodyBuilder.toString());

                // findAll method
                MethodSource<JavaClassSource> findAll = javaClass.addMethod();
                findAll.setVisibility(Visibility.PUBLIC).setFinal(true);
                findAll.setName("findAll" + Generator.upperCaseFirstChar(li.name()));
                findAll.setReturnTypeVoid();
                findAll.addParameter("greycat.Callback<" + li.type() + "[]>", "callback");
                StringBuilder findAllBodyBuilder = createFindMethodBody(li, null);
                findAll.setBody(findAllBodyBuilder.toString());
            }

        });

        init.setBody(initBodyBuilder.toString());

        typeAtBodyBuilder.append("return super.typeAt(index);");
        typeAt.setBody(typeAtBodyBuilder.toString());

        getAtBodyBuilder.append("return super.getAt(index);");
        getAt.setBody(getAtBodyBuilder.toString());

        setAtBodyBuilder.append("return super.setAt(index, type, value);");
        setAt.setBody(setAtBodyBuilder.toString());

        return javaClass;
    }

    private static StringBuilder createFindMethodBody(Index li, StringBuilder paramsBuilder) {
        StringBuilder findBodyBuilder = new StringBuilder();

        findBodyBuilder.append("greycat.Index index = this.getIndex(META.name);\n");
        findBodyBuilder.append("if (index != null) {");
        findBodyBuilder.append("index.find(new Callback<greycat.Node[]>() {");
        findBodyBuilder.append("@Override\n");
        findBodyBuilder.append("public void on(greycat.Node[] result) {");
        findBodyBuilder.append(li.type() + "[] typedResult = new " + li.type() + "[result.length];");
        findBodyBuilder.append("java.lang.System.arraycopy(result, 0, typedResult, 0, result.length);");
        findBodyBuilder.append("callback.on(typedResult);");
        findBodyBuilder.append("}");
        findBodyBuilder.append("},");
        findBodyBuilder.append("this.world(), this.time()");
        if (paramsBuilder != null) {
            findBodyBuilder.append(", " + paramsBuilder.toString());
        }
        findBodyBuilder.append(");");
        findBodyBuilder.append("}");
        findBodyBuilder.append("else {");
        findBodyBuilder.append("callback.on(null);");
        findBodyBuilder.append("}");

        return findBodyBuilder;
    }

    private static StringBuilder createAddOppositeBody(String edgeType, Edge edge) {
        StringBuilder oppositeBodyBuilder = new StringBuilder();
        String oppositeName;
        if (edge.opposite() != null) {
            if (edge.opposite().edge() instanceof Relation) {
                oppositeName = ((Relation) edge.opposite().edge()).name();
                oppositeBodyBuilder.append("value.addToRelation(").append(edgeType).append(".").append(oppositeName.toUpperCase()).append(".name, this);");

            } else if (edge.opposite().edge() instanceof Reference) {
                oppositeName = ((Reference) edge.opposite().edge()).name();
                oppositeBodyBuilder.append("value.removeFromRelation(").append(edgeType).append(".").append(oppositeName.toUpperCase()).append(".name, this);");
                oppositeBodyBuilder.append("value.addToRelation(").append(edgeType).append(".").append(oppositeName.toUpperCase()).append(".name, this);");

            } else if (edge.opposite().edge() instanceof Index) {
                Index idx = ((Index) edge.opposite().edge());
                oppositeName = idx.name();
                oppositeBodyBuilder.append("greycat.Index index = value.getIndex(").
                        append(Generator.upperCaseFirstChar(edgeType)).append(".").append(oppositeName.toUpperCase()).append(".name);");
                oppositeBodyBuilder.append("if (index == null) {");
                oppositeBodyBuilder.append("index = (greycat.Index) value.getOrCreate(").
                        append(Generator.upperCaseFirstChar(edgeType)).append(".").append(oppositeName.toUpperCase()).append(".name, Type.INDEX);");
                StringBuilder indexedAttBuilder = new StringBuilder();
                for (AttributeRef attRef : idx.attributes()) {
                    indexedAttBuilder.append(idx.type() + "." + attRef.ref().name().toUpperCase() + ".name");
                    indexedAttBuilder.append(",");
                }
                indexedAttBuilder.deleteCharAt(indexedAttBuilder.length() - 1);
                oppositeBodyBuilder.append("index.declareAttributes(null, " + indexedAttBuilder.toString() + ");");

                oppositeBodyBuilder.append("}");
                oppositeBodyBuilder.append("index.update(").append("this").append(");");
            }
        }
        return oppositeBodyBuilder;
    }

    private static StringBuilder createRemoveOppositeBody(String edgeType, Edge edge) {
        StringBuilder oppositeBodyBuilder = new StringBuilder();

        String oppositeName;
        if ((edge.opposite().edge() instanceof Relation) || (edge.opposite().edge() instanceof Reference)) {
            oppositeName = (edge.opposite().edge() instanceof Relation) ? ((Relation) edge.opposite().edge()).name() : ((Reference) edge.opposite().edge()).name();
            oppositeBodyBuilder.append("value.removeFromRelation(").append(edgeType).append(".").append(oppositeName.toUpperCase()).append(".name, self);");

        } else if (edge.opposite().edge() instanceof Index) {
            oppositeName = ((Index) edge.opposite().edge()).name();
            oppositeBodyBuilder.append("greycat.Index index = value.getIndex(").append(edgeType).append(".").append(oppositeName.toUpperCase()).append(".name);");
            oppositeBodyBuilder.append(" if (index != null) {");
            oppositeBodyBuilder.append("index.unindex(self);");
            oppositeBodyBuilder.append("}");
        }
        return oppositeBodyBuilder;
    }


}


