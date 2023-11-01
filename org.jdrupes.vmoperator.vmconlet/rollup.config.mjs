import typescript from 'rollup-plugin-typescript2';
import postcss from 'rollup-plugin-postcss';

let packagePath = "org/jdrupes/vmoperator/vmconlet";
let baseName = "VmConlet"
let module = "build/generated/resources/" + packagePath 
    +  "/" + baseName + "-functions.js";

let pathsMap = {
    "aash-plugin": "../../page-resource/aash-vue-components/lib/aash-vue-components.js",
    "jgconsole": "../../console-base-resource/jgconsole.js",
    "jgwc": "../../page-resource/jgwc-vue-components/jgwc-components.js",
    "l10nBundles": "./" + baseName + "-l10nBundles.ftl.js",
    "vue": "../../page-resource/vue/vue.esm-browser.js",
    "chartjs": "../../page-resource/chart.js/auto.js"
}

export default {
  external: ['aash-plugin', 'jgconsole', 'jgwc', 'l10nBundles', 'vue', 'chartjs'],
  input: "src/" + packagePath + "/browser/" + baseName + "-functions.ts",
  output: [
    {
      format: "esm",
      file: module,
      sourcemap: true,
      sourcemapPathTransform: (relativeSourcePath, _sourcemapPath) => {
        return relativeSourcePath.replace(/^([^/]*\/){12}/, "./");
      },
      paths: pathsMap
    }
  ],
  plugins: [
    typescript(),
    postcss()
  ]
};
