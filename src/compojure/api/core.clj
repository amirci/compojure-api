(ns compojure.api.core
  (:require [compojure.api.meta :as meta]
            [compojure.api.routes :as routes]
            [compojure.api.middleware :as mw]
            [compojure.core :as compojure]
            [clojure.tools.macro :as macro]))

(defn- handle [handlers request]
  (some #(% request) handlers))

(defn routes
  "Create a Ring handler by combining several handlers into one."
  [& handlers]
  (let [handlers (seq (keep identity handlers))]
    (routes/create nil nil {} (vec handlers) (partial handle handlers))))

(defn wrap-routes
  "Apply a middleware function to routes after they have been matched."
  ([handler middleware]
   (let [x-handler (compojure/wrap-routes handler middleware)]
     ;; use original handler for docs and wrapped handler for implementation
     (routes/create nil nil {} [handler] x-handler)))
  ([handler middleware & args]
   (wrap-routes handler #(apply middleware % args))))

(defmacro defroutes
  "Define a Ring handler function from a sequence of routes.
  The name may optionally be followed by a doc-string and metadata map."
  [name & routes]
  {:style/indent 1}
  (let [[name routes] (macro/name-with-attributes name routes)]
    `(def ~name (routes ~@routes))))

(defmacro let-routes
  "Takes a vector of bindings and a body of routes.

  Equivalent to: `(let [...] (routes ...))`"
  [bindings & body]
  {:style/indent 1}
  `(let ~bindings (routes ~@body)))

(defn undocumented
  "Routes without route-documentation. Can be used to wrap routes,
  not satisfying compojure.api.routes/Routing -protocol."
  [& handlers]
  (let [handlers (keep identity handlers)]
    (routes/create nil nil {} nil (partial handle handlers))))

(defn middleware
  "Wraps routes with given middlewares using thread-first macro.

  Note that middlewares will be executed even if routes in body
  do not match the request uri. Be careful with middlewares that
  have side-effects."
  [middleware & body]
  {:style/indent 1}
  (let [body (apply routes body)
        wrap-mw (mw/compose-middleware middleware)]
    (routes/create nil nil {} [body] (wrap-mw body))))

(defmacro context [& args] {:style/indent 2} (meta/restructure nil      args {:context? true}))

(defmacro GET     [& args] {:style/indent 2} (meta/restructure :get     args nil))
(defmacro ANY     [& args] {:style/indent 2} (meta/restructure nil      args nil))
(defmacro HEAD    [& args] {:style/indent 2} (meta/restructure :head    args nil))
(defmacro PATCH   [& args] {:style/indent 2} (meta/restructure :patch   args nil))
(defmacro DELETE  [& args] {:style/indent 2} (meta/restructure :delete  args nil))
(defmacro OPTIONS [& args] {:style/indent 2} (meta/restructure :options args nil))
(defmacro POST    [& args] {:style/indent 2} (meta/restructure :post    args nil))
(defmacro PUT     [& args] {:style/indent 2} (meta/restructure :put     args nil))
