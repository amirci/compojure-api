(ns compojure.api.middleware
  (:require [ring.util.response :refer [response content-type redirect]]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [ring.util.http-response :refer [bad-request internal-server-error]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            [ring.middleware.format-params :refer [wrap-restful-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            ring.middleware.http-response
            ring.swagger.middleware))

(defroutes public-resource-routes
  (GET "/" [] (redirect "/index.html"))
  (route/resources "/"))

(defn public-resources
  "serves public resources for missed requests"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (or response
        ((route/resources "/") request)))))

;; make this better, Slingshot?
(defn ex-info-support
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch clojure.lang.ExceptionInfo e
        (bad-request (ex-data e))))))

;; All mime-types supported by default mw-format
;; FIXME: Extension point to mw-format which can be used to modify req?
;; So that we can check which formats are enabled
(def mime-types (into {} (map (fn [[k x]]
                                (let [t (:enc-type x)]
                                  [k (str (:type t) "/" (:sub-type t))]))
                              ring.middleware.format-response/format-encoders)))

(def res-mime-types (map mime-types [:json :yaml :edn :clojure :yaml-in-html :transit-json :transit-msgpack]))

(def req-mime-types (map mime-types [:json-kw :edn :yaml-kw :transit-msgpack :transit-json]))

(defn wrap-swagger [handler]
  (fn [request]
    (-> request
        (assoc-in [:meta :consumes] req-mime-types)
        (assoc-in [:meta :produces] res-mime-types)
        handler)))

(defn handle-req-error [e handler req]
  (cond
    (instance? com.fasterxml.jackson.core.JsonParseException e)
    (bad-request {:type "json-parse-exception"
                  :message (.getMessage e)})

    (instance? org.yaml.snakeyaml.parser.ParserException e)
    (bad-request {:type "yaml-parse-exception"
                  :message (.getMessage e)})

    :else
    (internal-server-error {:type (str (class e))
                            :message (.getMessage e)})))

(defn serializable?
  "Predicate which return true if the response body is serializable.
   That is, return type is set by :return compojure-api key or it's not
   string, File or InputStream."
  [_ {:keys [body] :as response}]
  (when response
    (or (:compojure.api.meta/serializable? response)
        (coll? body))))

(defn api-middleware
  "opinionated chain of middlewares for web apis."
  [handler]
  (-> handler
      ring.middleware.http-response/catch-response
      ring.swagger.middleware/catch-validation-errors
      ex-info-support
      wrap-swagger
      (wrap-restful-params
        :formats [:json-kw :edn :yaml-kw :transit-msgpack :transit-json]
        :handle-error handle-req-error)
      (wrap-restful-response
        :predicate serializable?)
      wrap-keyword-params
      wrap-nested-params
      wrap-params))
