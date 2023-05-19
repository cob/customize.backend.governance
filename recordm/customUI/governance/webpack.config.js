const CopyWebpackPlugin = require('copy-webpack-plugin');
const CleanWebpackPlugin = require('clean-webpack-plugin');

module.exports = {
  plugins: [
    new CleanWebpackPlugin(['dist']),
    new CopyWebpackPlugin([
      {from: 'src/css',to:'css/'},
      {from: 'src/img',to:'img/'},
      {from: 'src/dashboard.html', to: 'dashboard.html'}
    ], {}),
  ],

  entry: './src/index.js',
  output: {
    filename: 'js/browser-bundle.js',
    path: __dirname +'/dist'
  },


  //devtool: 'source-map',
  externals: {
        // require("jquery") is external and available
        //  on the global var jQuery
        "jquery": "jQuery",
        "marked": "marked"
  },
  module: {
    loaders: [
      {
        test: /\.js$/,
        loader: 'babel-loader',
        query: {
          presets: ['env','es2015' ,'react']
        }
      }
    ]
  }

};
