/*
 *   Copyright 2018, Cordite Foundation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
import React from 'react'
import {Page} from 'containers/Page/Page';
import {Nav} from 'components/Nav/Nav';
import {Sidebar} from 'components/Sidebar/Sidebar';
import {getNodes, getNotaries, getBraidAPI, login} from 'scripts/restCalls';
import {isNotary, mutateNodes, sortNodes,} from 'scripts/processData';
import {headersList} from 'scripts/headersList'
import navOptions from 'navOptions.json';

export default class Default extends React.Component {
  constructor(props) {
    super(props)
    this.state = {
      nodes: [],
      notaries: [],
      page: 'home',
      braid: {}
    }
    
    this.getNodes = this.getNodes.bind(this);
    this.sortTable = this.sortTable.bind(this);
  }

  componentDidMount(){    
    this.getNodes();
  }

  getNodes = async function(){
    let notaries = await getNotaries();
    let nodes = await getNodes();
    nodes = await mutateNodes(nodes);
    nodes = isNotary(nodes, notaries);
    nodes = sortNodes('Organisational Unit', nodes, headersList);
    
    this.setState({
      nodes: nodes,
      notaries: notaries
    });
  }

  sortTable(e){
    let sortedNodes = sortNodes(e.target.dataset.header, this.state.nodes, headersList)
    this.setState({nodes: sortedNodes});
  }

  handleBtn = (event, data) => {
    const btnType = event.target.dataset.btn

    switch (btnType.toLowerCase()) {
      case 'swagger':
        window.location = "/swagger/"
        break
      case 'dashboard':
        this.setState({page: 'home'});
        break;
      case 'braid':
        // window.location = "/braid/api/"
        getBraidAPI()
        .then(result => {
          this.setState({
            braid: result,
            page: 'braid'
          });
        })
        
        break;
      default:
        break;
    }
  }

  render () {
    return (
      <div className='default-component'>
        <Nav toggleModal={this.props.toggleModal} />
        <div className="row">
          {this.props.admin ? 
            <Sidebar 
              navOptions={navOptions} 
              handleBtn={this.handleBtn}/>  : "" }
          <section id="main-content" className={"column" + (this.props.admin ? " column-offset-20" : "")}>
            <Page          
              headersList={headersList}
              nodes={this.state.nodes}
              notaries={this.state.notaries}
              page={this.state.page} 
              sortTable={this.sortTable}
              json={this.state.braid}
              toggleModal={this.props.toggleModal}
              admin={this.props.admin}
              getNodes={this.getNodes}
            /> 
          </section>
        </div>        
      </div>    
    )
  }
}